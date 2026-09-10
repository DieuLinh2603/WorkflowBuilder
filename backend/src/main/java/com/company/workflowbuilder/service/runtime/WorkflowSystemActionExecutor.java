package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.runtime.InstanceStepLog;
import com.company.workflowbuilder.entity.runtime.WorkflowInstance;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.repository.InstanceStepLogRepository;
import com.company.workflowbuilder.repository.WorkflowInstanceRepository;
import com.company.workflowbuilder.service.NotificationCenterService;
import com.company.workflowbuilder.service.NotificationStepDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class WorkflowSystemActionExecutor {
    private final WorkflowInstanceRepository instances;
    private final InstanceStepLogRepository logs;
    private final NotificationCenterService notificationCenter;
    private final NotificationStepDeliveryService notificationDelivery;
    private final WorkflowJsonCodec codec;

    @SuppressWarnings("unchecked")
    public boolean execute(WorkflowInstance instance, WorkflowStep step) {
        return execute(instance, step, List.of(codec.snapshot(instance.getFieldSnapshot())));
    }

    public boolean execute(WorkflowInstance instance, WorkflowStep step, List<Map<String, Object>> calculationRows) {
        Map<String, Object> action = codec.stepConfig(step);
        String actionType = Objects.toString(action.get("actionType"), "");
        if (actionType.isBlank()) return true;
        String policy = Objects.toString(action.get("failurePolicy"), "STOP");
        int attempts = "RETRY".equals(policy)
                ? Math.max(1, ((Number) action.getOrDefault("retryCount", 3)).intValue()) : 1;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                executeOnce(instance, step, action, actionType, calculationRows);
                audit(instance, step, "SYSTEM_ACTION_COMPLETED", actionType);
                return true;
            } catch (RuntimeException exception) {
                last = exception;
                audit(instance, step, "SYSTEM_ACTION_FAILED", exception.getMessage());
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void executeOnce(WorkflowInstance instance, WorkflowStep step, Map<String, Object> action,
            String actionType, List<Map<String, Object>> calculationRows) {
        Map<String, Object> snapshot = codec.snapshot(instance.getFieldSnapshot());
        List<Map<String, Object>> mappings = (List<Map<String, Object>>) action.getOrDefault("mappings", List.of());
        switch (actionType) {
            case "CALCULATE_OUTPUT" -> {
                var outputs = codec.calculatedOutputs(action.get("calculatedOutputs"));
                if (outputs.isEmpty()) throw new IllegalArgumentException("Cần ít nhất một cột output tính toán");
                var calculated = OutputCalculator.calculate(outputs, calculationRows);
                // The current row is first; aggregates use every supplied row.
                snapshot.putAll(calculated.get(0));
                saveSnapshot(instance, snapshot);
            }
            case "UPDATE_DATA" -> {
                for (Map<String, Object> mapping : mappings) {
                    String target = Objects.toString(mapping.get("targetField"), "");
                    if (!target.isBlank()) snapshot.put(target, mappingValue(mapping, snapshot, instance));
                }
                saveSnapshot(instance, snapshot);
            }
            case "UPDATE_STATUS" -> {
                snapshot.put("status", Objects.toString(action.get("newStatus"), ""));
                saveSnapshot(instance, snapshot);
            }
            case "CREATE_RECORD" -> {
                Map<String, Object> record = new LinkedHashMap<>();
                record.put("type", Objects.toString(action.get("recordType"), "Record"));
                for (Map<String, Object> mapping : mappings)
                    record.put(Objects.toString(mapping.get("targetField"), "field"),
                            mappingValue(mapping, snapshot, instance));
                snapshot.put("_lastCreatedRecord", record);
                saveSnapshot(instance, snapshot);
            }
            case "SEND_NOTIFICATION" -> sendNotification(instance, step, action, snapshot);
            case "API_CALL" -> callApi(action,
                    codec.render(Objects.toString(action.get("payloadTemplate"), "{}"), instance, snapshot));
            default -> throw new IllegalArgumentException("Unsupported System Action type: " + actionType);
        }
    }

    private void saveSnapshot(WorkflowInstance instance, Map<String, Object> snapshot) {
        instance.setFieldSnapshot(codec.write(snapshot));
        instances.save(instance);
    }

    private void sendNotification(WorkflowInstance instance, WorkflowStep step, Map<String, Object> action,
            Map<String, Object> snapshot) {
        String title = codec.render(Objects.toString(action.get("notificationTitle"), "Thông báo workflow"),
                instance, snapshot);
        String body = codec.render(Objects.toString(action.get("notificationBody"), instance.getRequestCode()),
                instance, snapshot);
        String channel = Objects.toString(action.get("notificationChannel"), "IN_APP");
        if ("IN_APP".equals(channel))
            notificationCenter.create(instance.getCreatedBy(), title, body, instance.getRequestCode(),
                    "/instances/" + instance.getId());
        else
            notificationDelivery.deliver(Set.of(channel), List.of(instance.getCreatedBy()), title, body,
                    Objects.toString(action.get("webhookUrl"), ""), instance, step);
    }

    private Object mappingValue(Map<String, Object> mapping, Map<String, Object> snapshot,
            WorkflowInstance instance) {
        String source = Objects.toString(mapping.get("sourceFieldKey"), "");
        return source.isBlank()
                ? codec.render(Objects.toString(mapping.get("valueTemplate"), ""), instance, snapshot)
                : snapshot.get(source);
    }

    private void callApi(Map<String, Object> action, String payload) {
        try {
            int timeout = Math.max(1, ((Number) action.getOrDefault("timeoutSeconds", 30)).intValue());
            String method = Objects.toString(action.get("httpMethod"), "POST").toUpperCase(Locale.ROOT);
            HttpRequest.BodyPublisher body = "GET".equals(method) || "DELETE".equals(method)
                    ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(payload);
            HttpRequest request = HttpRequest.newBuilder(URI.create(Objects.toString(action.get("endpointUrl"))))
                    .timeout(Duration.ofSeconds(timeout)).header("Content-Type", "application/json")
                    .method(method, body).build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request,
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new IllegalStateException("API returned HTTP " + response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("API call interrupted", exception);
        } catch (java.io.IOException | IllegalArgumentException exception) {
            throw new IllegalStateException("Cannot call configured API: " + exception.getMessage(), exception);
        }
    }

    private void audit(WorkflowInstance instance, WorkflowStep step, String action, String comment) {
        logs.save(InstanceStepLog.builder().instance(instance).step(step).action(action).comment(comment).build());
    }
}
