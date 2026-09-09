package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowFieldValidationService {
    private final CustomFieldDefinitionRepository fields;

    public void validateSubmission(UUID stepId, Map<String, Object> values) {
        var definitions = fields.findByStepIdOrderByDisplayOrderAsc(stepId);
        Set<String> allowed = definitions.stream().map(CustomFieldDefinition::getFieldKey).collect(Collectors.toSet());
        values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("Field is not defined in Start Step: " + key);
        });
        definitions.forEach(field -> validateRequiredValue(field, values.get(field.getFieldKey())));
    }

    public Map<String, Object> sanitizeDraft(UUID stepId, Map<String, Object> submitted) {
        Map<String, Object> source = submitted == null ? Map.of() : submitted;
        Map<String, Object> result = new LinkedHashMap<>();
        for (CustomFieldDefinition field : fields.findByStepIdOrderByDisplayOrderAsc(stepId)) {
            if (!source.containsKey(field.getFieldKey()))
                continue;
            Object value = source.get(field.getFieldKey());
            if (!isEmpty(value))
                validateType(field, value, "Invalid draft value for field: ");
            result.put(field.getFieldKey(), value);
        }
        return result;
    }

    public void validateStep(UUID stepId, Map<String, Object> values) {
        fields.findByStepIdOrderByDisplayOrderAsc(stepId)
                .forEach(field -> validateRequiredValue(field, values.get(field.getFieldKey())));
    }

    private void validateRequiredValue(CustomFieldDefinition field, Object value) {
        if (field.isRequired() && (isEmpty(value) || Boolean.FALSE.equals(value)))
            throw new IllegalArgumentException("Required field is missing: " + field.getLabel());
        if (!isEmpty(value))
            validateType(field, value, "Invalid value for field: ");
    }

    private void validateType(CustomFieldDefinition field, Object value, String errorPrefix) {
        boolean valid = switch (field.getType()) {
            case TEXT -> value instanceof String;
            case NUMBER -> value instanceof Number || isNumber(value);
            case DATE -> isDate(value);
            case CHECKBOX -> value instanceof Boolean;
            case FILE -> value instanceof Map<?, ?> file
                    && file.get("name") instanceof String name && !name.isBlank()
                    && file.get("dataUrl") instanceof String;
        };
        if (!valid)
            throw new IllegalArgumentException(errorPrefix + field.getLabel());
    }

    private boolean isEmpty(Object value) {
        return value == null || value instanceof String text && text.isBlank();
    }

    private boolean isNumber(Object value) {
        try {
            new BigDecimal(String.valueOf(value));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isDate(Object value) {
        try {
            LocalDate.parse(String.valueOf(value));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }
}
