package com.company.workflowbuilder.service.data;

import com.company.workflowbuilder.dto.response.BatchInstanceResponse;
import com.company.workflowbuilder.entity.data.DatasetRecord;
import com.company.workflowbuilder.entity.data.DatasetVersion;
import com.company.workflowbuilder.entity.data.WorkflowDataBinding;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.DatasetRecordRepository;
import com.company.workflowbuilder.repository.DatasetVersionRepository;
import com.company.workflowbuilder.repository.WorkflowDataBindingRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.company.workflowbuilder.service.WorkflowEngineService;
import com.company.workflowbuilder.service.NotificationCenterService;
import com.company.workflowbuilder.service.runtime.WorkflowFieldValidationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkflowDataBindingExecutionService {
    private final WorkflowDataBindingRepository bindings;
    private final DatasetVersionRepository versions;
    private final DatasetRecordRepository records;
    private final WorkflowStepRepository steps;
    private final CustomFieldDefinitionRepository fieldDefinitions;
    private final WorkflowFieldValidationService fieldValidation;
    private final WorkflowEngineService workflowEngine;
    private final NotificationCenterService notifications;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<UUID> automaticBindingIds(UUID datasetVersionId) {
        DatasetVersion version = version(datasetVersionId);
        return bindings.findByDatasetIdAndActiveTrue(version.getDataset().getId()).stream()
                .filter(binding -> "AUTO_ON_DATASET_SUCCESS".equals(binding.getTriggerMode()))
                .map(WorkflowDataBinding::getId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchInstanceResponse consume(UUID bindingId, UUID datasetVersionId) {
        WorkflowDataBinding binding = binding(bindingId);
        DatasetVersion current = version(datasetVersionId);
        if (!binding.getDataset().getId().equals(current.getDataset().getId()))
            throw new IllegalArgumentException("Dataset version không thuộc Data Binding này");
        if (current.getVersionNumber() <= binding.getLastConsumedVersion()) return null;

        Map<String, String> oldChecksums = previousChecksums(binding);
        Map<String, Object> mapping = jsonMap(binding.getMappingJson());
        List<Map<String, Object>> filters = jsonList(binding.getFilterJson());
        WorkflowStep start = steps.findByWorkflowIdOrderByPositionXAsc(binding.getWorkflow().getId()).stream()
                .filter(step -> step.getType() == StepType.START)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow không có START Step"));
        Map<String, CustomFieldDefinition> targets = fieldDefinitions
                .findByStepIdOrderByDisplayOrderAsc(start.getId()).stream()
                .collect(Collectors.toMap(CustomFieldDefinition::getFieldKey, field -> field));

        List<Map<String, Object>> selected = new ArrayList<>();
        int sourceRow = 0;
        for (DatasetRecord record : records.findByDatasetVersionId(current.getId())) {
            sourceRow++;
            if (Objects.equals(oldChecksums.get(record.getBusinessKey()), record.getChecksum())) continue;
            Map<String, Object> payload = jsonMap(record.getPayloadJson());
            if (!filters.stream().allMatch(filter -> matches(payload, filter))) continue;

            Map<String, Object> mapped = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : mapping.entrySet()) {
                String targetKey = String.valueOf(entry.getValue());
                CustomFieldDefinition target = targets.get(targetKey);
                if (target == null)
                    throw new IllegalArgumentException("Field START không tồn tại: " + targetKey);
                mapped.put(targetKey, convert(payload.get(entry.getKey()), target, sourceRow));
            }
            try {
                fieldValidation.validateSubmission(start.getId(), mapped);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Dòng dữ liệu " + sourceRow + ": " + exception.getMessage(), exception);
            }
            mapped.put("_pipelineBusinessKey", record.getBusinessKey());
            mapped.put("_pipelineChecksum", record.getChecksum());
            mapped.put("_sourceDatasetVersionId", current.getId().toString());
            selected.add(mapped);
        }

        BatchInstanceResponse result = selected.isEmpty() ? null
                : workflowEngine.submitPipelineBatchAs(binding.getWorkflow().getId(), selected, binding.getCreatedBy());
        binding.setLastConsumedVersion(current.getVersionNumber());
        bindings.save(binding);
        return result;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void notifyAutomaticFailure(UUID bindingId, String detail) {
        WorkflowDataBinding binding = binding(bindingId);
        String message = detail == null || detail.isBlank() ? "Không xác định được lỗi" : detail;
        notifications.create(binding.getCreatedBy(), "Không thể đưa Dataset vào Workflow",
                "Pipeline đã tạo Dataset thành công nhưng Data Binding “" + binding.getWorkflow().getName()
                        + "” bị lỗi: " + message,
                null, "/workflows");
    }

    private Object convert(Object value, CustomFieldDefinition target, int row) {
        if (value == null) return null;
        try {
            return switch (target.getType()) {
                case TEXT -> String.valueOf(value);
                case NUMBER -> value instanceof Number ? value : new BigDecimal(String.valueOf(value).trim());
                case DATE -> LocalDate.parse(String.valueOf(value).trim()).toString();
                case CHECKBOX -> booleanValue(value);
                case FILE -> value;
            };
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Dòng dữ liệu " + row + ": không thể chuyển giá trị của field “"
                    + target.getLabel() + "” sang kiểu " + target.getType(), exception);
        }
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) return bool;
        String normalized = String.valueOf(value).trim().toLowerCase();
        if (Set.of("true", "1", "yes", "có").contains(normalized)) return true;
        if (Set.of("false", "0", "no", "không").contains(normalized)) return false;
        throw new IllegalArgumentException("Invalid boolean");
    }

    private Map<String, String> previousChecksums(WorkflowDataBinding binding) {
        Map<String, String> result = new HashMap<>();
        if (binding.getLastConsumedVersion() > 0) {
            versions.findByDatasetIdAndVersionNumber(binding.getDataset().getId(), binding.getLastConsumedVersion())
                    .ifPresent(version -> records.findByDatasetVersionId(version.getId())
                            .forEach(record -> result.put(record.getBusinessKey(), record.getChecksum())));
        }
        return result;
    }

    private boolean matches(Map<String, Object> row, Map<String, Object> filter) {
        Object actual = row.get(String.valueOf(filter.get("field")));
        Object expected = filter.get("value");
        String operator = String.valueOf(filter.getOrDefault("operator", "EQUALS"));
        return switch (operator) {
            case "EQUALS" -> Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "NOT_EQUALS" -> !Objects.equals(String.valueOf(actual), String.valueOf(expected));
            case "GT" -> number(actual).compareTo(number(expected)) > 0;
            case "GTE" -> number(actual).compareTo(number(expected)) >= 0;
            case "LT" -> number(actual).compareTo(number(expected)) < 0;
            case "LTE" -> number(actual).compareTo(number(expected)) <= 0;
            case "CONTAINS" -> actual != null && String.valueOf(actual).contains(String.valueOf(expected));
            case "IN" -> expected instanceof Collection<?> values
                    && values.stream().map(String::valueOf).anyMatch(String.valueOf(actual)::equals);
            default -> false;
        };
    }

    private BigDecimal number(Object value) {
        return new BigDecimal(String.valueOf(value));
    }

    private WorkflowDataBinding binding(UUID id) {
        return bindings.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowDataBinding", "id", id));
    }

    private DatasetVersion version(UUID id) {
        return versions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("DatasetVersion", "id", id));
    }

    private Map<String, Object> jsonMap(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Mapping của Data Binding không hợp lệ", exception);
        }
    }

    private List<Map<String, Object>> jsonList(String value) {
        try {
            return mapper.readValue(value, new TypeReference<>() {});
        } catch (Exception exception) {
            throw new IllegalStateException("Filter của Data Binding không hợp lệ", exception);
        }
    }
}
