package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.runtime.WorkflowInstance;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class WorkflowJsonCodec {
    private final ObjectMapper mapper;

    public Map<String, Object> stepConfig(WorkflowStep step) {
        if (step.getConfigJson() == null || step.getConfigJson().isBlank())
            return new HashMap<>();
        try {
            return mapper.readValue(step.getConfigJson(), new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid config for step " + step.getLabel(), exception);
        }
    }

    public Map<String, Object> snapshot(String json) {
        try {
            return mapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Invalid instance field snapshot", exception);
        }
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize workflow data", exception);
        }
    }

    public String render(String value, WorkflowInstance instance, Map<String, Object> snapshot) {
        String rendered = template(value, instance);
        for (Map.Entry<String, Object> entry : snapshot.entrySet()) {
            String replacement = Objects.toString(entry.getValue(), "");
            rendered = rendered.replace("{{" + entry.getKey() + "}}", replacement)
                    .replace("{" + entry.getKey() + "}", replacement);
        }
        return rendered;
    }

    public String template(String value, WorkflowInstance instance) {
        return value.replace("{{requestCode}}", instance.getRequestCode())
                .replace("{{workflowName}}", instance.getWorkflow().getName());
    }
}
