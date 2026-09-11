package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.data.DataConnector;
import com.company.workflowbuilder.entity.runtime.SystemActionExecution;
import com.company.workflowbuilder.repository.DataConnectorRepository;
import com.company.workflowbuilder.service.data.DataConnectorService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.io.InputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

@Service
@RequiredArgsConstructor
public class RestConnectorHttpClient {
    private static final int MAX_RESPONSE_BYTES = 1024 * 1024;
    private final DataConnectorRepository connectors;
    private final DataConnectorService connectorService;
    private final ObjectMapper mapper;

    public Result execute(SystemActionExecution execution, int timeoutSeconds) {
        try {
            DataConnector connector = connectors.findById(execution.getConnector().getId())
                    .orElseThrow(() -> new IllegalStateException("REST connector was deleted"));
            if (!connector.isActive()) throw new IllegalStateException("REST connector is disabled");
            Map<String, Object> connectorConfig = mapper.readValue(connector.getConfigJson(), new TypeReference<>() {});
            URI base = URI.create(Objects.toString(connectorConfig.get("baseUrl"), ""));
            URI target = URI.create(execution.getRequestUrl());
            if (!Objects.equals(base.getScheme(), target.getScheme()) || !Objects.equals(base.getHost(), target.getHost())
                    || effectivePort(base) != effectivePort(target))
                throw new IllegalStateException("Queued request no longer matches the connector host");
            HttpRequest.Builder builder = HttpRequest.newBuilder(target)
                    .timeout(Duration.ofSeconds(Math.max(1, Math.min(300, timeoutSeconds))));
            Map<String, String> headers = mapper.readValue(execution.getRequestHeadersJson(), new TypeReference<>() {});
            headers.forEach(builder::header);
            builder.header("Content-Type", "application/json");
            applyAuth(builder, connectorService.credentials(connector));
            HttpRequest.BodyPublisher body = execution.getRequestBody() == null
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(execution.getRequestBody());
            builder.method(execution.getHttpMethod(), body);
            HttpResponse<InputStream> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(Math.max(1, Math.min(300, timeoutSeconds))))
                    .build().send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
            byte[] bytes;
            try (InputStream stream = response.body()) { bytes = stream.readNBytes(MAX_RESPONSE_BYTES + 1); }
            if (bytes.length > MAX_RESPONSE_BYTES) return new Result(response.statusCode(), null,
                    "API response exceeds 1 MB", false);
            String responseBody = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            int status = response.statusCode();
            if (status >= 200 && status < 300) return new Result(status, responseBody, null, false);
            boolean retryable = status == 408 || status == 429 || status >= 500;
            return new Result(status, responseBody, "API returned HTTP " + status, retryable);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return new Result(null, null, "API call interrupted", true);
        } catch (java.io.IOException ex) {
            return new Result(null, null, "Cannot call connector API: " + ex.getMessage(), true);
        } catch (RuntimeException ex) {
            return new Result(null, null, ex.getMessage(), false);
        } catch (Exception ex) {
            return new Result(null, null, "Cannot prepare connector request: " + ex.getMessage(), false);
        }
    }

    private void applyAuth(HttpRequest.Builder request, Map<String, Object> credentials) {
        String type = Objects.toString(credentials.getOrDefault("authType", "NONE"));
        if ("BEARER".equalsIgnoreCase(type))
            request.header("Authorization", "Bearer " + Objects.toString(credentials.get("token"), ""));
        if ("API_KEY".equalsIgnoreCase(type))
            request.header(Objects.toString(credentials.getOrDefault("headerName", "X-API-Key")),
                    Objects.toString(credentials.get("apiKey"), ""));
    }
    private int effectivePort(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : "https".equals(uri.getScheme()) ? 443 : 80; }

    public record Result(Integer statusCode, String body, String error, boolean retryable) {}
}
