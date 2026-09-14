package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.data.DataConnector;
import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemActionQueueService {
    private static final int MAX_REQUEST_CHARS = 256 * 1024;
    private static final Set<String> BLOCKED_HEADERS = Set.of("authorization", "host", "content-length");
    private final SystemActionExecutionRepository executions;
    private final DataConnectorRepository connectors;
    private final WorkflowJsonCodec codec;
    private final ObjectMapper mapper;

    @Transactional
    @SuppressWarnings("unchecked")
    public SystemActionExecution enqueue(WorkflowInstance instance, WorkflowStep step,
            Map<String, Object> snapshot, Integer batchRowNumber) {
        Map<String, Object> config = codec.stepConfig(step);
        UUID connectorId = UUID.fromString(Objects.toString(config.get("connectorId"), ""));
        DataConnector connector = connectors.findById(connectorId)
                .orElseThrow(() -> new IllegalStateException("Configured REST connector was not found"));
        if (!connector.isActive() || !"REST".equals(connector.getConnectorType()))
            throw new IllegalStateException("Configured REST connector is disabled or has the wrong type");

        String method = Objects.toString(config.get("httpMethod"), "POST").toUpperCase(Locale.ROOT);
        String path = renderPath(Objects.toString(config.get("pathTemplate"), ""), instance, snapshot);
        URI requestUri = requestUri(connector, path,
                (List<Map<String, Object>>) config.getOrDefault("queryParams", List.of()), instance, snapshot);
        Map<String, String> headers = renderHeaders(
                (List<Map<String, Object>>) config.getOrDefault("headers", List.of()), instance, snapshot);
        String body = null;
        if (Set.of("POST", "PUT", "PATCH").contains(method)) {
            body = codec.render(Objects.toString(config.get("payloadTemplate"), "{}"), instance, snapshot);
            if (body.length() > MAX_REQUEST_CHARS) throw new IllegalArgumentException("Rendered API payload exceeds 256 KB");
            try { mapper.readTree(body); }
            catch (Exception ex) { throw new IllegalArgumentException("Rendered API payload is not valid JSON", ex); }
        }
        int maxAttempts = number(config.get("maxAttempts"), legacyAttempts(config));
        Map<String, Object> responseSelection = new LinkedHashMap<>(asMap(config.get("responseSelection")));
        if (responseSelection.containsKey("expectedValueTemplate")) {
            responseSelection.put("expectedValue", codec.render(
                    Objects.toString(responseSelection.get("expectedValueTemplate"), ""), instance, snapshot));
            responseSelection.remove("expectedValueTemplate");
        }
        SystemActionExecution execution = executions.save(SystemActionExecution.builder()
                .instance(instance).step(step).connector(connector).batchRowNumber(batchRowNumber)
                .status(SystemActionExecutionStatus.QUEUED).maxAttempts(Math.max(1, Math.min(10, maxAttempts)))
                .timeoutSeconds(Math.max(1, Math.min(300, number(config.get("timeoutSeconds"), 30))))
                .httpMethod(method).requestUrl(requestUri.toString()).requestHeadersJson(codec.write(headers))
                .requestBody(body).responseMappingsJson(codec.write(config.getOrDefault("responseMappings", List.of())))
                .responseSelectorJson(codec.write(responseSelection))
                .nextAttemptAt(LocalDateTime.now()).build());
        headers.put("Idempotency-Key", execution.getId().toString());
        headers.put("X-Workflow-Execution-Id", execution.getId().toString());
        execution.setRequestHeadersJson(codec.write(headers));
        return executions.save(execution);
    }

    private URI requestUri(DataConnector connector, String renderedPath, List<Map<String, Object>> query,
            WorkflowInstance instance, Map<String, Object> snapshot) {
        Map<String, Object> connectorConfig;
        try { connectorConfig = mapper.readValue(connector.getConfigJson(), new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Stored connector JSON is invalid", ex); }
        URI base = URI.create(Objects.toString(connectorConfig.get("baseUrl"), ""));
        String cleanPath = renderedPath == null ? "" : renderedPath.trim();
        if (cleanPath.contains("://") || cleanPath.startsWith("//") || cleanPath.contains("#"))
            throw new IllegalArgumentException("API path must be relative to the connector base URL");
        String baseText = base.toString().replaceAll("/+$", "");
        String pathText = cleanPath.isBlank() ? "" : "/" + cleanPath.replaceAll("^/+", "");
        StringBuilder url = new StringBuilder(baseText).append(pathText);
        boolean first = !url.toString().contains("?");
        for (Map<String, Object> item : query) {
            String name = Objects.toString(item.get("name"), "").trim();
            if (name.isBlank()) continue;
            String value = codec.render(Objects.toString(item.get("valueTemplate"), ""), instance, snapshot);
            url.append(first ? '?' : '&'); first = false;
            url.append(encode(name)).append('=').append(encode(value));
        }
        URI result = URI.create(url.toString());
        if (!Objects.equals(base.getScheme(), result.getScheme()) || !Objects.equals(base.getHost(), result.getHost())
                || effectivePort(base) != effectivePort(result))
            throw new IllegalArgumentException("API path cannot override connector host");
        return result;
    }

    private Map<String, String> renderHeaders(List<Map<String, Object>> configured, WorkflowInstance instance,
            Map<String, Object> snapshot) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Map<String, Object> item : configured) {
            String name = Objects.toString(item.get("name"), "").trim();
            if (name.isBlank()) continue;
            if (BLOCKED_HEADERS.contains(name.toLowerCase(Locale.ROOT)))
                throw new IllegalArgumentException("Header is managed by the connector: " + name);
            result.put(name, codec.render(Objects.toString(item.get("valueTemplate"), ""), instance, snapshot));
        }
        return result;
    }

    private int legacyAttempts(Map<String, Object> config) {
        return "RETRY".equals(config.get("failurePolicy")) ? number(config.get("retryCount"), 3) : 1;
    }
    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map<?, ?> source)) return Map.of();
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) -> result.put(Objects.toString(key), item));
        return result;
    }
    private String renderPath(String template, WorkflowInstance instance, Map<String, Object> snapshot) {
        String result = template.replace("{{requestCode}}", encode(instance.getRequestCode()))
                .replace("{{workflowName}}", encode(instance.getWorkflow().getName()));
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            String value = encode(Objects.toString(entry.getValue(), ""));
            result = result.replace("{{" + entry.getKey() + "}}", value)
                    .replace("{" + entry.getKey() + "}", value);
        }
        return result;
    }
    private int number(Object value, int fallback) { return value instanceof Number n ? n.intValue() : fallback; }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20"); }
    private int effectivePort(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80; }
}
