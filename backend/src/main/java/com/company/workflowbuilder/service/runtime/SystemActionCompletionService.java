package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.repository.SystemActionExecutionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemActionCompletionService {
    private final SystemActionExecutionRepository executions;
    private final WorkflowJsonCodec codec;
    private final ObjectMapper mapper;

    @Transactional
    public void complete(UUID id, RestConnectorHttpClient.Result result) {
        SystemActionExecution execution = executions.findById(id).orElseThrow();
        if (execution.getStatus() != SystemActionExecutionStatus.RUNNING) return;
        execution.setResponseStatus(result.statusCode());
        execution.setResponseBody(SystemActionExecutionStateService.auditBody(result.body()));
        Map<String, Object> outputs = new LinkedHashMap<>();
        boolean success = result.error() == null;
        String error = result.error();
        if (success) {
            try { outputs.putAll(mapResponse(execution, result.body())); }
            catch (RuntimeException ex) { success = false; error = "Response mapping failed: " + ex.getMessage(); }
        }
        execution.setMappedOutputsJson(codec.write(outputs));
        execution.setErrorMessage(SystemActionExecutionStateService.limit(error, 2000));
        execution.setStatus(success ? SystemActionExecutionStatus.SUCCEEDED : SystemActionExecutionStatus.FAILED);
        execution.setCompletedAt(LocalDateTime.now());
        executions.save(execution);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapResponse(SystemActionExecution execution, String body) {
        List<Map<String, Object>> mappings;
        try { mappings = mapper.readValue(execution.getResponseMappingsJson(), new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Stored response mappings are invalid", ex); }
        if (mappings.isEmpty()) return Map.of();
        Object json;
        try { json = mapper.readValue(Objects.toString(body, ""), Object.class); }
        catch (Exception ex) { throw new IllegalArgumentException("API response is not valid JSON"); }
        json = selectResponse(execution, json);
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Map<String, Object> mapping : mappings) {
            String path = Objects.toString(mapping.get("jsonPath"), "");
            String target = Objects.toString(mapping.get("targetField"), "");
            Optional<Object> value = SimpleJsonPath.read(json, path);
            if (value.isPresent()) outputs.put(target, value.get());
            else if (mapping.containsKey("defaultValue") && mapping.get("defaultValue") != null)
                outputs.put(target, mapping.get("defaultValue"));
            else if (Boolean.TRUE.equals(mapping.get("required")))
                throw new IllegalArgumentException("Required JSONPath was not found: " + path);
        }
        return outputs;
    }

    private Object selectResponse(SystemActionExecution execution, Object root) {
        Map<String, Object> selector;
        try { selector = mapper.readValue(execution.getResponseSelectorJson(), new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Stored response selector is invalid", ex); }
        String mode = Objects.toString(selector.get("mode"), "ROOT").toUpperCase(Locale.ROOT);
        if ("ROOT".equals(mode)) return root;
        String collectionPath = Objects.toString(selector.get("collectionJsonPath"), "$");
        Object collection = SimpleJsonPath.read(root, collectionPath)
                .orElseThrow(() -> new IllegalArgumentException("Response collection JSONPath was not found: " + collectionPath));
        if (!(collection instanceof List<?> values))
            throw new IllegalArgumentException("Selected response value is not an array: " + collectionPath);
        List<?> candidates = values;
        if (mode.startsWith("FILTER_")) {
            String filterPath = Objects.toString(selector.get("filterJsonPath"), "$");
            String expected = Objects.toString(selector.get("expectedValue"), "");
            candidates = values.stream().filter(item -> SimpleJsonPath.read(item, filterPath)
                    .map(value -> Objects.equals(Objects.toString(value, ""), expected)).orElse(false)).toList();
        }
        if (candidates.isEmpty()) throw new IllegalArgumentException("Response selection did not match any item");
        return mode.endsWith("LAST") ? candidates.get(candidates.size() - 1) : candidates.get(0);
    }
}
