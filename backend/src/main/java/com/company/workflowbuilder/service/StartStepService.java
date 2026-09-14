package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.CustomFieldCreateRequest;
import com.company.workflowbuilder.dto.FieldOption;
import com.company.workflowbuilder.dto.request.StartStepConfigRequest;
import com.company.workflowbuilder.dto.response.CustomFieldResponse;
import com.company.workflowbuilder.dto.response.StartStepConfigResponse;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.field.FieldType;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.workflow.AudienceType;
import com.company.workflowbuilder.entity.workflow.WorkflowAudience;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.UserGroupRepository;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.repository.WorkflowAudienceRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartStepService {

    private final WorkflowStepRepository workflowStepRepository;
    private final CustomFieldDefinitionRepository customFieldRepository;
    private final ObjectMapper objectMapper;
    private final WorkflowAuthorizationService authorization;
    private final WorkflowAudienceRepository audienceRepository;
    private final UserGroupRepository groupRepository;
    private final UserRepository userRepository;
    @Autowired(required=false) private FormService formService;

    @Transactional(readOnly = true)
    public StartStepConfigResponse getStartConfig(UUID workflowId, UUID stepId) {
        WorkflowStep step = findStepAndValidateWorkflow(workflowId, stepId);
        authorization.requireView(step.getWorkflow());
        
        // Parse configJson
        Map<String, Object> config = parseConfig(step.getConfigJson());
        List<WorkflowAudience> audience = audienceRepository.findByWorkflowId(workflowId);
        boolean allEmployees = audience.isEmpty() || audience.stream()
                .anyMatch(rule -> rule.getSubjectType() == AudienceType.ALL_ACTIVE);
        List<String> allowedGroupIds = audience.stream()
                .filter(rule -> rule.getSubjectType() == AudienceType.GROUP)
                .map(WorkflowAudience::getSubjectValue)
                .distinct()
                .toList();
        List<String> allowedUserIds = audience.stream()
                .filter(rule -> rule.getSubjectType() == AudienceType.USER)
                .map(WorkflowAudience::getSubjectValue)
                .distinct()
                .toList();
        List<SystemRole> allowedRoles = audience.stream()
                .filter(rule -> rule.getSubjectType() == AudienceType.SYSTEM_ROLE)
                .map(rule -> parseSystemRole(rule.getSubjectValue()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        
        List<CustomFieldResponse> fields = step.getWorkflow().getFormVersion()!=null&&formService!=null
                ? formService.fieldResponses(step.getWorkflow().getFormVersion())
                : customFieldRepository.findByStepIdOrderByDisplayOrderAsc(stepId).stream().map(this::toFieldResponse).toList();
        var formVersion=step.getWorkflow().getFormVersion();
                
        return StartStepConfigResponse.builder()
                .instructionForCreator(formVersion==null?(String)config.get("instructionForCreator"):formVersion.getInstruction())
                .requesterScope(allEmployees ? "ALL_EMPLOYEES" : "SPECIFIC_GROUP_ROLE")
                .allowedUserIds(allowedUserIds)
                .allowedGroupIds(allowedGroupIds)
                .allowedRoles(allowedRoles)
                .allowRequesterWithdrawal(!Boolean.FALSE.equals(config.get("allowRequesterWithdrawal")))
                .submissionMode(formVersion==null?("BATCH".equals(config.get("submissionMode"))?"BATCH":"SINGLE"):formVersion.getSubmissionMode())
                .recordRecipientFieldKey(formVersion==null?(String)config.get("recordRecipientFieldKey"):formVersion.getRecordRecipientFieldKey())
                .maxBatchRows(formVersion==null?(config.get("maxBatchRows") instanceof Number number?number.intValue():500):formVersion.getMaxBatchRows())
                .fields(fields)
                .build();
    }

    @Transactional(readOnly = true)
    public List<CustomFieldResponse> getCustomFields(UUID workflowId, UUID stepId) {
        WorkflowStep step = findStepAndValidateWorkflow(workflowId, stepId);
        authorization.requireView(step.getWorkflow());
        return customFieldRepository.findByStepIdOrderByDisplayOrderAsc(stepId)
                .stream().map(this::toFieldResponse).toList();
    }

    @Transactional
    public void updateStartConfig(UUID workflowId, UUID stepId, StartStepConfigRequest request) {
        WorkflowStep step = findStepAndValidateWorkflow(workflowId, stepId);
        validateDraftStatus(step);

        String requesterScope = request.getRequesterScope();
        if (!"ALL_EMPLOYEES".equals(requesterScope) && !"SPECIFIC_GROUP_ROLE".equals(requesterScope)) {
            throw new IllegalArgumentException("Requester scope must be ALL_EMPLOYEES or SPECIFIC_GROUP_ROLE");
        }
        List<UUID> groupIds = "ALL_EMPLOYEES".equals(requesterScope) || request.getAllowedGroupIds() == null
                ? List.of() : new ArrayList<>(new LinkedHashSet<>(request.getAllowedGroupIds()));
        List<UUID> userIds = "ALL_EMPLOYEES".equals(requesterScope) || request.getAllowedUserIds() == null
                ? List.of() : new ArrayList<>(new LinkedHashSet<>(request.getAllowedUserIds()));
        List<SystemRole> roles = "ALL_EMPLOYEES".equals(requesterScope) || request.getAllowedRoles() == null
                ? List.of() : new ArrayList<>(new LinkedHashSet<>(request.getAllowedRoles()));
        if ("SPECIFIC_GROUP_ROLE".equals(requesterScope) && userIds.isEmpty() && groupIds.isEmpty() && roles.isEmpty()) {
            throw new IllegalArgumentException("Hãy chọn ít nhất một user, nhóm hoặc vai trò được phép tạo request");
        }
        for (UUID userId : userIds) {
            if (userRepository.findById(userId).filter(user -> user.isActive()).isEmpty()) {
                throw new IllegalArgumentException("Không tìm thấy user active: " + userId);
            }
        }
        for (UUID groupId : groupIds) {
            if (!groupRepository.existsById(groupId)) {
                throw new ResourceNotFoundException("UserGroup", "id", groupId);
            }
        }

        String submissionMode = "BATCH".equalsIgnoreCase(request.getSubmissionMode()) ? "BATCH" : "SINGLE";
        List<CustomFieldDefinition> startFields = customFieldRepository.findByStepIdOrderByDisplayOrderAsc(stepId);
        if ("BATCH".equals(submissionMode) && startFields.isEmpty())
            throw new IllegalArgumentException("Chế độ danh sách CSV yêu cầu ít nhất một field");
        if ("BATCH".equals(submissionMode) && startFields.stream().anyMatch(field -> field.getType() == FieldType.FILE))
            throw new IllegalArgumentException("Chế độ danh sách CSV không hỗ trợ field FILE; hãy dùng cột URL dạng TEXT");
        int maxBatchRows = request.getMaxBatchRows() == null ? 500 : request.getMaxBatchRows();
        if (maxBatchRows < 1 || maxBatchRows > 2000)
            throw new IllegalArgumentException("Số dòng mỗi batch phải nằm trong khoảng 1-2000");
        String recipientFieldKey = request.getRecordRecipientFieldKey() == null
                ? null : request.getRecordRecipientFieldKey().trim();
        if (recipientFieldKey != null && recipientFieldKey.isBlank()) recipientFieldKey = null;
        if (recipientFieldKey != null) {
            String finalRecipientFieldKey = recipientFieldKey;
            boolean exists = startFields.stream().anyMatch(field -> field.getFieldKey().equals(finalRecipientFieldKey)
                    && field.getType() == FieldType.TEXT);
            if (!exists)
                throw new IllegalArgumentException("Trường người nhận phải là field TEXT tồn tại: " + recipientFieldKey);
        }
        
        Map<String, Object> config = parseConfig(step.getConfigJson());
        config.put("instructionForCreator", request.getInstructionForCreator());
        config.put("requesterScope", requesterScope);
        config.put("allowedUserIds", userIds);
        config.put("allowedGroupIds", groupIds);
        config.put("allowedRoles", roles);
        config.put("allowRequesterWithdrawal", !Boolean.FALSE.equals(request.getAllowRequesterWithdrawal()));
        config.put("submissionMode", submissionMode);
        config.put("recordRecipientFieldKey", recipientFieldKey);
        config.put("maxBatchRows", maxBatchRows);

        audienceRepository.deleteByWorkflowId(workflowId);
        audienceRepository.flush();
        if ("ALL_EMPLOYEES".equals(requesterScope)) {
            saveAudience(step, AudienceType.ALL_ACTIVE, "*");
        } else {
            userIds.forEach(userId -> saveAudience(step, AudienceType.USER, userId.toString()));
            groupIds.forEach(groupId -> saveAudience(step, AudienceType.GROUP, groupId.toString()));
            roles.forEach(role -> saveAudience(step, AudienceType.SYSTEM_ROLE, role.name()));
        }

        step.setConfigJson(writeConfig(config));
        workflowStepRepository.save(step);
    }

    @Transactional
    public CustomFieldResponse createCustomField(UUID workflowId, UUID stepId, CustomFieldCreateRequest request) {
        WorkflowStep step = findStepAndValidateWorkflow(workflowId, stepId);
        validateDraftStatus(step);
        rejectBatchFileField(step, request);
        validateFieldConfiguration(request);
        
        String fieldKey = request.getFieldKey();
        if (fieldKey == null || fieldKey.trim().isEmpty()) {
            fieldKey = generateFieldKey(request.getLabel());
        }
        
        if (customFieldRepository.existsByStepWorkflowIdAndFieldKey(workflowId, fieldKey)) {
            throw new IllegalArgumentException("Field key '" + fieldKey + "' đã tồn tại trong bước này.");
        }
        
        List<CustomFieldDefinition> existingFields = customFieldRepository.findByStepIdOrderByDisplayOrderAsc(stepId);
        int nextOrder = existingFields.isEmpty() ? 0 : existingFields.get(existingFields.size() - 1).getDisplayOrder() + 1;
        
        CustomFieldDefinition field = CustomFieldDefinition.builder()
                .step(step)
                .fieldKey(fieldKey)
                .label(request.getLabel())
                .type(request.getType())
                .required(request.isRequired())
                .placeholder(request.getPlaceholder())
                .configurationJson(writeFieldConfiguration(request))
                .displayOrder(nextOrder)
                .build();
                
        CustomFieldDefinition saved = customFieldRepository.save(field);
        return toFieldResponse(saved);
    }

    @Transactional
    public CustomFieldResponse updateCustomField(UUID workflowId, UUID stepId, UUID fieldId, CustomFieldCreateRequest request) {
        WorkflowStep step = findStepAndValidateWorkflow(workflowId, stepId);
        validateDraftStatus(step);
        rejectBatchFileField(step, request);
        validateFieldConfiguration(request);
        
        CustomFieldDefinition field = customFieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomField", "id", fieldId));
                
        if (!field.getStep().getId().equals(stepId)) {
            throw new IllegalArgumentException("Field không thuộc step này");
        }
        
        String newFieldKey = request.getFieldKey();
        if (newFieldKey == null || newFieldKey.trim().isEmpty()) {
            newFieldKey = generateFieldKey(request.getLabel());
        }
        String recipientField = (String) parseConfig(step.getConfigJson()).get("recordRecipientFieldKey");
        if (field.getFieldKey().equals(recipientField)
                && (!field.getFieldKey().equals(newFieldKey) || request.getType() != FieldType.TEXT))
            throw new IllegalStateException("Field người nhận CSV phải giữ nguyên key và kiểu TEXT; hãy bỏ cấu hình người nhận trước");
        
        // If key changes, check uniqueness
        if (!field.getFieldKey().equals(newFieldKey) && customFieldRepository.existsByStepWorkflowIdAndFieldKey(workflowId, newFieldKey)) {
            throw new IllegalArgumentException("Field key '" + newFieldKey + "' đã tồn tại trong bước này.");
        }
        
        field.setFieldKey(newFieldKey);
        field.setLabel(request.getLabel());
        field.setType(request.getType());
        field.setRequired(request.isRequired());
        field.setPlaceholder(request.getPlaceholder());
        field.setConfigurationJson(writeFieldConfiguration(request));
        
        CustomFieldDefinition saved = customFieldRepository.save(field);
        return toFieldResponse(saved);
    }

    @Transactional
    public void deleteCustomField(UUID workflowId, UUID stepId, UUID fieldId) {
        WorkflowStep step = findStepAndValidateWorkflow(workflowId, stepId);
        validateDraftStatus(step);
        
        CustomFieldDefinition field = customFieldRepository.findById(fieldId)
                .orElseThrow(() -> new ResourceNotFoundException("CustomField", "id", fieldId));
                
        if (!field.getStep().getId().equals(stepId)) {
            throw new IllegalArgumentException("Field không thuộc step này");
        }
        String recipientField = (String) parseConfig(step.getConfigJson()).get("recordRecipientFieldKey");
        if (field.getFieldKey().equals(recipientField))
            throw new IllegalStateException("Không thể xóa field đang được dùng làm người nhận theo dòng CSV");
        
        // TODO: In Phase 3 (Runtime), check if InstanceFieldValue exists for this field.
        // For now, in Phase 1 (Drafting), we can freely delete.
        
        customFieldRepository.delete(field);
    }

    // --- Helpers ---

    private WorkflowStep findStepAndValidateWorkflow(UUID workflowId, UUID stepId) {
        WorkflowStep step = workflowStepRepository.findById(stepId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", stepId));
        if (!step.getWorkflow().getId().equals(workflowId)) {
            throw new IllegalArgumentException("Step không thuộc workflow này");
        }
        return step;
    }

    private void validateDraftStatus(WorkflowStep step) {
        authorization.requireEdit(step.getWorkflow());
        if (step.getWorkflow().getStatus() != WorkflowStatus.DRAFT) {
            throw new IllegalStateException("Chỉ có thể sửa cấu hình Start Step khi Workflow ở trạng thái DRAFT.");
        }
    }

    private void rejectBatchFileField(WorkflowStep step, CustomFieldCreateRequest request) {
        if (request.getType() == FieldType.FILE
                && "BATCH".equals(parseConfig(step.getConfigJson()).get("submissionMode")))
            throw new IllegalArgumentException("Chế độ danh sách CSV không hỗ trợ field FILE; hãy dùng cột URL dạng TEXT");
    }

    private void validateFieldConfiguration(CustomFieldCreateRequest request) {
        List<FieldOption> options = request.getOptions() == null ? List.of() : request.getOptions();
        if (Set.of(FieldType.SELECT, FieldType.MULTI_CHOICE, FieldType.RADIO).contains(request.getType())) {
            if (options.isEmpty())
                throw new IllegalArgumentException("Field lựa chọn phải có ít nhất một option");
            Set<String> values = new java.util.HashSet<>();
            for (FieldOption option : options) {
                String label = option == null ? "" : Objects.toString(option.getLabel(), "").trim();
                String value = option == null ? "" : Objects.toString(option.getValue(), "").trim();
                if (label.isBlank() || value.isBlank())
                    throw new IllegalArgumentException("Nhãn và giá trị option không được để trống");
                if (!values.add(value))
                    throw new IllegalArgumentException("Giá trị option bị trùng: " + value);
            }
        }
        if (request.getType() != FieldType.USER_PICKER && request.isAllowMultiple())
            throw new IllegalArgumentException("Chỉ USER_PICKER hỗ trợ chọn nhiều user");
    }

    private String writeFieldConfiguration(CustomFieldCreateRequest request) {
        Map<String, Object> config = new HashMap<>();
        if (Set.of(FieldType.SELECT, FieldType.MULTI_CHOICE, FieldType.RADIO).contains(request.getType()))
            config.put("options", request.getOptions());
        if (request.getType() == FieldType.USER_PICKER)
            config.put("allowMultiple", request.isAllowMultiple());
        return writeConfig(config);
    }

    private String generateFieldKey(String label) {
        if (label == null || label.trim().isEmpty()) return "field_" + UUID.randomUUID().toString().substring(0, 8);
        
        String temp = Normalizer.normalize(label.trim(), Normalizer.Form.NFD);
        Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String slug = pattern.matcher(temp).replaceAll("").toLowerCase();
        
        slug = slug.replaceAll("đ", "d");
        slug = slug.replaceAll("[^a-z0-9]+", "_"); // replace non-alphanumeric with underscore
        
        if (slug.endsWith("_")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug;
    }

    private Map<String, Object> parseConfig(String json) {
        if (json == null || json.trim().isEmpty()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse configJson: {}", json, e);
            return new HashMap<>();
        }
    }

    private String writeConfig(Map<String, Object> config) {
        try {
            return objectMapper.writeValueAsString(config);
        } catch (JsonProcessingException e) {
            log.error("Failed to write configJson", e);
            return "{}";
        }
    }

    private void saveAudience(WorkflowStep step, AudienceType type, String value) {
        audienceRepository.save(WorkflowAudience.builder()
                .workflow(step.getWorkflow())
                .subjectType(type)
                .subjectValue(value)
                .build());
    }

    private SystemRole parseSystemRole(String value) {
        try {
            return SystemRole.valueOf(value);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return null;
        }
    }
    
    private CustomFieldResponse toFieldResponse(CustomFieldDefinition field) {
        Map<String, Object> config = parseConfig(field.getConfigurationJson());
        List<FieldOption> options = objectMapper.convertValue(config.getOrDefault("options", List.of()),
                new TypeReference<List<FieldOption>>() {});
        return CustomFieldResponse.builder()
                .id(field.getId())
                .fieldKey(field.getFieldKey())
                .label(field.getLabel())
                .type(field.getType())
                .required(field.isRequired())
                .placeholder(field.getPlaceholder())
                .displayOrder(field.getDisplayOrder())
                .options(options)
                .allowMultiple(Boolean.TRUE.equals(config.get("allowMultiple")))
                .build();
    }
}
