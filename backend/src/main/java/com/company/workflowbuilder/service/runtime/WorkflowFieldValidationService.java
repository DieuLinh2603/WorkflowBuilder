package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.form.FormField;
import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.dto.FieldOption;
import com.company.workflowbuilder.dto.response.CustomFieldResponse;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.FormFieldRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.company.workflowbuilder.repository.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class WorkflowFieldValidationService {
    private final CustomFieldDefinitionRepository fields;
    private final UserRepository users;
    private final ObjectMapper objectMapper;
    private final FormFieldRepository formFields;
    private final WorkflowStepRepository steps;

    /** Backwards-compatible constructor used by focused unit tests. */
    public WorkflowFieldValidationService(CustomFieldDefinitionRepository fields) {
        this(fields, null, new ObjectMapper(), null, null);
    }

    public WorkflowFieldValidationService(CustomFieldDefinitionRepository fields, UserRepository users,
            ObjectMapper objectMapper) {
        this(fields, users, objectMapper, null, null);
    }

    public void validateSubmission(UUID stepId, Map<String, Object> values) {
        List<FormField> formDefinitions=formDefinitions(stepId);
        if(formDefinitions!=null){
            Set<String> allowed=formDefinitions.stream().map(FormField::getFieldKey).collect(Collectors.toSet());
            rejectUnknown(values,allowed,"Field is not defined in Form: ");
            formDefinitions.forEach(field->validateRequiredValue(field,values.get(field.getFieldKey())));
            return;
        }
        var definitions = fields.findByStepIdOrderByDisplayOrderAsc(stepId);
        Set<String> allowed = definitions.stream().map(CustomFieldDefinition::getFieldKey).collect(Collectors.toSet());
        values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("Field is not defined in Start Step: " + key);
        });
        definitions.forEach(field -> validateRequiredValue(field, values.get(field.getFieldKey())));
    }

    public List<CustomFieldResponse> definitions(UUID stepId) {
        List<FormField> formDefinitions=formDefinitions(stepId);
        if(formDefinitions!=null)return formDefinitions.stream().map(this::response).toList();
        return fields.findByStepIdOrderByDisplayOrderAsc(stepId).stream().map(field -> {
            Map<String, Object> config = fieldConfig(field);
            List<FieldOption> options = objectMapper.convertValue(config.getOrDefault("options", List.of()),
                    new TypeReference<List<FieldOption>>() {});
            return CustomFieldResponse.builder().id(field.getId()).fieldKey(field.getFieldKey())
                    .label(field.getLabel()).type(field.getType()).required(field.isRequired())
                    .placeholder(field.getPlaceholder()).displayOrder(field.getDisplayOrder())
                    .options(options).allowMultiple(Boolean.TRUE.equals(config.get("allowMultiple"))).build();
        }).toList();
    }

    public Map<String, Object> sanitizeDraft(UUID stepId, Map<String, Object> submitted) {
        Map<String, Object> source = submitted == null ? Map.of() : submitted;
        Map<String, Object> result = new LinkedHashMap<>();
        List<FormField> formDefinitions=formDefinitions(stepId);
        if(formDefinitions!=null){for(FormField field:formDefinitions){if(!source.containsKey(field.getFieldKey()))continue;Object value=source.get(field.getFieldKey());if(!isEmpty(value))validateType(field,value,"Invalid draft value for field: ");result.put(field.getFieldKey(),value);}return result;}
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

    public Map<String, Object> validateStepOutput(UUID stepId, Map<String, Object> submitted) {
        Map<String, Object> values = submitted == null ? Map.of() : submitted;
        List<CustomFieldDefinition> definitions = fields.findByStepIdOrderByDisplayOrderAsc(stepId);
        Set<String> allowed = definitions.stream().map(CustomFieldDefinition::getFieldKey).collect(Collectors.toSet());
        values.keySet().stream().filter(key -> !allowed.contains(key)).findFirst().ifPresent(key -> {
            throw new IllegalArgumentException("Field is not defined as output of current step: " + key);
        });
        definitions.forEach(field -> validateRequiredValue(field, values.get(field.getFieldKey())));
        return new LinkedHashMap<>(values);
    }

    private void validateRequiredValue(CustomFieldDefinition field, Object value) {
        if (field.isRequired() && (isEmpty(value) || Boolean.FALSE.equals(value)))
            throw new IllegalArgumentException("Required field is missing: " + field.getLabel());
        if (!isEmpty(value))
            validateType(field, value, "Invalid value for field: ");
    }

    private void validateRequiredValue(FormField field,Object value){if(field.isRequired()&&(isEmpty(value)||Boolean.FALSE.equals(value)))throw new IllegalArgumentException("Required field is missing: "+field.getLabel());if(!isEmpty(value))validateType(field,value,"Invalid value for field: ");}

    private void validateType(CustomFieldDefinition field, Object value, String errorPrefix) {
        boolean valid = switch (field.getType()) {
            case TEXT -> value instanceof String;
            case NUMBER -> value instanceof Number || isNumber(value);
            case DATE -> isDate(value);
            case DATETIME -> isDateTime(value);
            case CHECKBOX -> value instanceof Boolean;
            case SELECT, RADIO -> value instanceof String text && optionValues(field).contains(text);
            case MULTI_CHOICE -> value instanceof Collection<?> values && values.stream()
                    .allMatch(item -> item instanceof String text && optionValues(field).contains(text));
            case USER_PICKER -> validUsers(field, value);
            case FILE -> value instanceof Map<?, ?> file
                    && file.get("name") instanceof String name && !name.isBlank()
                    && file.get("dataUrl") instanceof String;
            default -> true;
        };
        if (!valid)
            throw new IllegalArgumentException(errorPrefix + field.getLabel());
    }

    private void validateType(FormField field,Object value,String errorPrefix){boolean valid=switch(field.getType()){case TEXT->value instanceof String;case NUMBER->value instanceof Number||isNumber(value);case DATE->isDate(value);case DATETIME->isDateTime(value);case CHECKBOX->value instanceof Boolean;case SELECT,RADIO->value instanceof String text&&formOptionValues(field).contains(text);case MULTI_CHOICE->value instanceof Collection<?> values&&values.stream().allMatch(item->item instanceof String text&&formOptionValues(field).contains(text));case USER_PICKER->validFormUsers(field,value);case FILE->value instanceof Map<?,?> file&&file.get("name") instanceof String name&&!name.isBlank()&&file.get("dataUrl") instanceof String;};if(!valid)throw new IllegalArgumentException(errorPrefix+field.getLabel());}

    private boolean isEmpty(Object value) {
        return value == null || value instanceof String text && text.isBlank()
                || value instanceof Collection<?> collection && collection.isEmpty();
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

    private boolean isDateTime(Object value) {
        try {
            LocalDateTime.parse(String.valueOf(value));
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private Set<String> optionValues(CustomFieldDefinition field) {
        try {
            Map<String, Object> config = fieldConfig(field);
            List<Map<String, Object>> options = objectMapper.convertValue(config.getOrDefault("options", List.of()),
                    new TypeReference<List<Map<String, Object>>>() {});
            return options.stream().map(option -> String.valueOf(option.get("value"))).collect(Collectors.toSet());
        } catch (Exception ignored) {
            return Set.of();
        }
    }

    private boolean validUsers(CustomFieldDefinition field, Object value) {
        boolean multiple = false;
        try {
            Map<String, Object> config = fieldConfig(field);
            multiple = Boolean.TRUE.equals(config.get("allowMultiple"));
        } catch (Exception ignored) { }
        Collection<?> values = multiple && value instanceof Collection<?> collection ? collection : List.of(value);
        if (multiple != (value instanceof Collection<?>)) return false;
        try {
            return users != null && !values.isEmpty() && values.stream().allMatch(item ->
                    users.findById(UUID.fromString(String.valueOf(item))).filter(user -> user.isActive()).isPresent());
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, Object> fieldConfig(CustomFieldDefinition field) {
        try {
            return objectMapper.readValue(field.getConfigurationJson(), new TypeReference<>() {});
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private List<FormField> formDefinitions(UUID stepId){if(formFields==null||steps==null)return null;return steps.findById(stepId).filter(step->step.getType()==StepType.START&&step.getWorkflow().getFormVersion()!=null).map(step->formFields.findByFormVersionIdOrderByDisplayOrderAsc(step.getWorkflow().getFormVersion().getId())).orElse(null);}
    private void rejectUnknown(Map<String,Object> values,Set<String> allowed,String prefix){values.keySet().stream().filter(key->!allowed.contains(key)).findFirst().ifPresent(key->{throw new IllegalArgumentException(prefix+key);});}
    private CustomFieldResponse response(FormField field){Map<String,Object> config=formConfig(field);List<FieldOption> options=objectMapper.convertValue(config.getOrDefault("options",List.of()),new TypeReference<List<FieldOption>>(){});return CustomFieldResponse.builder().id(field.getId()).fieldKey(field.getFieldKey()).label(field.getLabel()).type(field.getType()).required(field.isRequired()).placeholder(field.getPlaceholder()).displayOrder(field.getDisplayOrder()).options(options).allowMultiple(Boolean.TRUE.equals(config.get("allowMultiple"))).build();}
    private Map<String,Object> formConfig(FormField field){try{return objectMapper.readValue(field.getConfigurationJson(),new TypeReference<>(){});}catch(Exception ignored){return Map.of();}}
    private Set<String> formOptionValues(FormField field){List<Map<String,Object>> options=objectMapper.convertValue(formConfig(field).getOrDefault("options",List.of()),new TypeReference<>(){});return options.stream().map(option->String.valueOf(option.get("value"))).collect(Collectors.toSet());}
    private boolean validFormUsers(FormField field,Object value){boolean multiple=Boolean.TRUE.equals(formConfig(field).get("allowMultiple"));Collection<?> values=multiple&&value instanceof Collection<?> collection?collection:List.of(value);if(multiple!=(value instanceof Collection<?>))return false;try{return users!=null&&!values.isEmpty()&&values.stream().allMatch(item->users.findById(UUID.fromString(String.valueOf(item))).filter(user->user.isActive()).isPresent());}catch(Exception ignored){return false;}}
}
