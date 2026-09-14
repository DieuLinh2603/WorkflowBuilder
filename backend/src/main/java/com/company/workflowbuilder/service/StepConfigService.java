package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.*;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.user.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.time.LocalDate;

@Service @RequiredArgsConstructor
public class StepConfigService {
    private final WorkflowStepRepository steps;
    private final UserRepository users;
    private final CustomFieldDefinitionRepository fields;
    private final WorkflowAuthorizationService authorization;
    private final ObjectMapper mapper;

    @Transactional
    public Map<String,Object> configureApproval(UUID workflowId, UUID stepId, ApprovalConfigRequest request) {
        WorkflowStep step = step(workflowId, stepId, StepType.APPROVAL); authorization.requireEdit(step.getWorkflow());
        if (request.getMode() == ApprovalConfigRequest.Mode.MANUAL) {
            ApprovalConfigRequest.ApproverMode approverMode = request.getApproverMode() == null
                    ? ApprovalConfigRequest.ApproverMode.FIXED_USER : request.getApproverMode();
            if (approverMode == ApprovalConfigRequest.ApproverMode.FIXED_USER) {
                normalizeFixedUsers(request.getFixedUserEmail(), request.getActorUserIds(),
                        request::setFixedUserEmail, request::setActorUserIds, "approver");
            }
            if (approverMode == ApprovalConfigRequest.ApproverMode.ROLE_BASED
                    && (request.getActorRole() == null || request.getActorRole().isBlank()))
                throw new IllegalArgumentException("Role Based approval requires a role");
            if (approverMode == ApprovalConfigRequest.ApproverMode.ROLE_BASED
                    && users.findByJobTitleIgnoreCaseAndActiveTrue(request.getActorRole().trim()).isEmpty())
                throw new IllegalArgumentException("Không có tài khoản active nào thuộc chức danh: " + request.getActorRole());
            if (approverMode == ApprovalConfigRequest.ApproverMode.DYNAMIC && request.getDynamicActorSource() == null)
                throw new IllegalArgumentException("Dynamic approval requires an actor source");
        }
        if (request.getMode() == ApprovalConfigRequest.Mode.AUTO)
            validateAutoApprovalConditions(step, request);
        if (request.getDeadlineHours() != null && request.getDeadlineHours() <= 0)
            throw new IllegalArgumentException("Deadline must be greater than zero");
        if (request.getDeadlineDate() != null && request.getDeadlineDate().isBefore(LocalDate.now()))
            throw new IllegalArgumentException("Deadline date cannot be in the past");
        validateCompletionPolicy(request.getCompletionMode(), request.getCompletionPercentage());
        Map<String,Object> config = mapper.convertValue(request, Map.class);
        step.setConfigJson(write(config)); steps.save(step); return config;
    }

    @Transactional
    public Map<String,Object> configureNotification(UUID workflowId, UUID stepId, NotificationStepConfigRequest request) {
        WorkflowStep step = step(workflowId, stepId, StepType.NOTIFICATION); authorization.requireEdit(step.getWorkflow());
        Set<String> allowedChannels = Set.of("IN_APP", "EMAIL", "TEAMS", "WEBHOOK");
        Set<String> allowedTriggers = Set.of("STEP_ACTIVATED", "APPROVED", "REJECTED", "COMPLETED");
        if (request.getChannels() == null || request.getChannels().isEmpty())
            throw new IllegalArgumentException("Notification Step requires at least one channel");
        if (!allowedChannels.containsAll(request.getChannels()))
            throw new IllegalArgumentException("Notification Step contains an unsupported channel");
        if (request.getTriggers() == null || request.getTriggers().isEmpty())
            throw new IllegalArgumentException("Notification Step requires at least one trigger");
        if (!allowedTriggers.containsAll(request.getTriggers()))
            throw new IllegalArgumentException("Notification Step contains an unsupported trigger");
        if (request.getChannels().contains("WEBHOOK")) {
            String url = request.getWebhookUrl() == null ? "" : request.getWebhookUrl().trim();
            try {
                java.net.URI uri = java.net.URI.create(url);
                if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                    throw new IllegalArgumentException();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("Webhook channel requires a valid HTTP(S) URL");
            }
            request.setWebhookUrl(url);
        }
        Map<String,Object> config = mapper.convertValue(request, Map.class);
        config.put("delivery", "MULTI_CHANNEL");
        step.setConfigJson(write(config)); steps.save(step); return config;
    }

    @Transactional
    public Map<String,Object> configureReview(UUID workflowId, UUID stepId, ReviewConfigRequest request) {
        WorkflowStep step = step(workflowId, stepId, StepType.REVIEW); authorization.requireEdit(step.getWorkflow());
        normalizeActor(request.getApproverMode(), request.getFixedUserEmail(), request.getActorUserIds(),
                request.getActorRole(), request.getDynamicActorSource(), request::setFixedUserEmail, request::setActorUserIds);
        validateDeadline(request.getDeadlineHours(), request.getDeadlineDate());
        validateCompletionPolicy(request.getCompletionMode(), request.getCompletionPercentage());
        Map<String,Object> config = mapper.convertValue(request, Map.class);
        config.put("mode", "MANUAL");
        step.setConfigJson(write(config)); steps.save(step); return config;
    }

    @Transactional
    public Map<String,Object> configureAssignment(UUID workflowId, UUID stepId, AssignmentConfigRequest request) {
        WorkflowStep step = step(workflowId, stepId, StepType.ASSIGNMENT); authorization.requireEdit(step.getWorkflow());
        if (request.getAssignmentTargetType() == AssignmentConfigRequest.TargetType.USER && request.getActorUserIds().isEmpty())
            throw new IllegalArgumentException("Assignment requires at least one user");
        if (request.getAssignmentTargetType() == AssignmentConfigRequest.TargetType.GROUP && request.getAssignmentGroupIds().isEmpty())
            throw new IllegalArgumentException("Assignment requires at least one group");
        validateDeadline(request.getDeadlineHours(), request.getDeadlineDate());
        validateCompletionPolicy(request.getCompletionMode(), request.getCompletionPercentage());
        Map<String,Object> config = mapper.convertValue(request, Map.class);
        config.put("approverMode", "FIXED_USER");
        step.setConfigJson(write(config)); steps.save(step); return config;
    }

    @Transactional
    public Map<String,Object> configureSystemAction(UUID workflowId, UUID stepId, SystemActionConfigRequest request) {
        WorkflowStep step = step(workflowId, stepId, StepType.SYSTEM_ACTION); authorization.requireEdit(step.getWorkflow());
        requireDraft(step);
        Set<String> fieldKeys = new HashSet<>();
        fields.findByStepWorkflowId(workflowId).forEach(field -> fieldKeys.add(field.getFieldKey()));
        for (SystemActionConfigRequest.Mapping mapping : request.getMappings()) {
            if (mapping.getSourceFieldKey() != null && !mapping.getSourceFieldKey().isBlank()
                    && !fieldKeys.contains(mapping.getSourceFieldKey()))
                throw new IllegalArgumentException("Trường nguồn không tồn tại trong workflow: " + mapping.getSourceFieldKey());
            if (request.getActionType() == SystemActionConfigRequest.ActionType.UPDATE_DATA
                    && !fieldKeys.contains(mapping.getTargetField()))
                throw new IllegalArgumentException("Trường cần cập nhật không tồn tại trong workflow: " + mapping.getTargetField());
        }
        switch (request.getActionType()) {
            case API_CALL -> {
                validateHttpUrl(request.getEndpointUrl());
                if (!Set.of("GET", "POST", "PUT", "PATCH", "DELETE").contains(request.getHttpMethod().toUpperCase(Locale.ROOT)))
                    throw new IllegalArgumentException("HTTP method không được hỗ trợ");
                request.setHttpMethod(request.getHttpMethod().toUpperCase(Locale.ROOT));
            }
            case SEND_NOTIFICATION -> {
                if (request.getNotificationTitle() == null || request.getNotificationTitle().isBlank()
                        || request.getNotificationBody() == null || request.getNotificationBody().isBlank())
                    throw new IllegalArgumentException("Gửi thông báo yêu cầu tiêu đề và nội dung");
                if (!Set.of("IN_APP", "EMAIL", "TEAMS", "WEBHOOK").contains(request.getNotificationChannel()))
                    throw new IllegalArgumentException("Kênh thông báo không được hỗ trợ");
                if ("WEBHOOK".equals(request.getNotificationChannel())) validateHttpUrl(request.getWebhookUrl());
            }
            case UPDATE_DATA, CREATE_RECORD -> {
                if (request.getMappings().isEmpty()) throw new IllegalArgumentException("Action yêu cầu ít nhất một mapping dữ liệu");
                if (request.getActionType() == SystemActionConfigRequest.ActionType.CREATE_RECORD
                        && (request.getRecordType() == null || request.getRecordType().isBlank()))
                    throw new IllegalArgumentException("Tạo record yêu cầu loại record");
            }
            case UPDATE_STATUS -> {
                if (request.getNewStatus() == null || request.getNewStatus().isBlank())
                    throw new IllegalArgumentException("Cập nhật trạng thái yêu cầu trạng thái mới");
            }
        }
        Map<String,Object> config = mapper.convertValue(request, Map.class);
        step.setConfigJson(write(config)); steps.save(step); return config;
    }

    @Transactional
    public Map<String,Object> configureEnd(UUID workflowId, UUID stepId, EndStepConfigRequest request) {
        WorkflowStep step = step(workflowId, stepId, StepType.END); authorization.requireEdit(step.getWorkflow());
        requireDraft(step);
        Map<String,Object> config = mapper.convertValue(request, Map.class);
        step.setConfigJson(write(config)); steps.save(step); return config;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> get(UUID workflowId, UUID stepId) {
        WorkflowStep step = step(workflowId, stepId, null); authorization.requireView(step.getWorkflow());
        if (step.getConfigJson() == null || step.getConfigJson().isBlank()) return new HashMap<>();
        try { return mapper.readValue(step.getConfigJson(), Map.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid step configuration"); }
    }

    private WorkflowStep step(UUID workflowId, UUID id, StepType expected) {
        WorkflowStep step = steps.findById(id).orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", id));
        if (!step.getWorkflow().getId().equals(workflowId)) throw new IllegalArgumentException("Step does not belong to workflow");
        if (expected != null && step.getType() != expected) throw new IllegalArgumentException("Invalid step type");
        return step;
    }
    private void normalizeActor(ApprovalConfigRequest.ApproverMode mode,String email,List<UUID> ids,String role,
                                ApprovalConfigRequest.DynamicActorSource dynamic,
                                java.util.function.Consumer<String> emailSetter,
                                java.util.function.Consumer<List<UUID>> idsSetter) {
        if(mode==ApprovalConfigRequest.ApproverMode.FIXED_USER){
            normalizeFixedUsers(email, ids, emailSetter, idsSetter, "reviewer");
        }else if(mode==ApprovalConfigRequest.ApproverMode.ROLE_BASED){
            if(role==null||role.isBlank()||users.findByJobTitleIgnoreCaseAndActiveTrue(role.trim()).isEmpty())
                throw new IllegalArgumentException("Không có tài khoản active thuộc chức danh đã chọn");
        }else if(mode==ApprovalConfigRequest.ApproverMode.DYNAMIC&&dynamic==null)
            throw new IllegalArgumentException("Dynamic mode requires an actor source");
    }
    private void normalizeFixedUsers(String email, List<UUID> ids,
                                     java.util.function.Consumer<String> emailSetter,
                                     java.util.function.Consumer<List<UUID>> idsSetter,
                                     String actorLabel) {
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(ids == null ? List.of() : ids);
        if (uniqueIds.isEmpty()) {
            String normalized = email == null ? "" : email.trim().toLowerCase();
            if (!normalized.isBlank()) {
                User user = users.findByEmailIgnoreCase(normalized).filter(User::isActive)
                        .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản active với email: " + normalized));
                uniqueIds.add(user.getId());
            }
        }
        if (uniqueIds.isEmpty())
            throw new IllegalArgumentException("Fixed User requires at least one " + actorLabel);
        for (UUID id : uniqueIds)
            if (users.findById(id).filter(User::isActive).isEmpty())
                throw new IllegalArgumentException("Không tìm thấy tài khoản active: " + id);
        idsSetter.accept(new ArrayList<>(uniqueIds));
        emailSetter.accept("");
    }
    private void validateDeadline(Integer hours,LocalDate date){
        if(hours!=null&&hours<=0)throw new IllegalArgumentException("Deadline must be greater than zero");
        if(date!=null&&date.isBefore(LocalDate.now()))throw new IllegalArgumentException("Deadline date cannot be in the past");
    }
    private void validateCompletionPolicy(AssignmentConfigRequest.CompletionMode mode, Integer percentage) {
        if (mode == AssignmentConfigRequest.CompletionMode.PERCENTAGE
                && (percentage == null || percentage < 1 || percentage > 100))
            throw new IllegalArgumentException("Tỷ lệ hoàn thành phải nằm trong khoảng 1-100%");
    }
    private void validateAutoApprovalConditions(WorkflowStep approvalStep, ApprovalConfigRequest request) {
        if (request.getAutoConditions() == null || request.getAutoConditions().isEmpty())
            throw new IllegalArgumentException("Auto approval requires at least one condition");
        if (request.getLogicalOperator() == null)
            request.setLogicalOperator(LogicalOperator.AND);
        Map<String, CustomFieldDefinition> availableFields = new HashMap<>();
        fields.findByStepWorkflowId(approvalStep.getWorkflow().getId()).stream()
                .filter(field -> !field.getStep().getId().equals(approvalStep.getId()))
                .forEach(field -> availableFields.putIfAbsent(field.getFieldKey(), field));
        for (ConnectionUpsertRequest.Clause condition : request.getAutoConditions()) {
            CustomFieldDefinition field = availableFields.get(condition.getFieldKey());
            if (field == null)
                throw new IllegalArgumentException("Auto approval field does not exist: " + condition.getFieldKey());
            if (!operatorsFor(field).contains(condition.getOperator()))
                throw new IllegalArgumentException("Operator " + condition.getOperator() + " is not valid for field " + condition.getFieldKey());
            if (condition.getOperator() != ConditionOperator.IS_EMPTY
                    && condition.getOperator() != ConditionOperator.NOT_EMPTY
                    && (condition.getExpectedValue() == null || condition.getExpectedValue().isBlank()))
                throw new IllegalArgumentException("Auto approval condition requires an expected value: " + condition.getFieldKey());
        }
    }
    private Set<ConditionOperator> operatorsFor(CustomFieldDefinition field) {
        return switch (field.getType()) {
            case TEXT -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ, ConditionOperator.CONTAINS,
                    ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY);
            case NUMBER, DATE -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ, ConditionOperator.GT,
                    ConditionOperator.GTE, ConditionOperator.LT, ConditionOperator.LTE,
                    ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY);
            case CHECKBOX -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ,
                    ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY);
            case FILE -> EnumSet.of(ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY);
            default -> EnumSet.of(ConditionOperator.EQ, ConditionOperator.NEQ, ConditionOperator.IS_EMPTY, ConditionOperator.NOT_EMPTY);
        };
    }
    private void requireDraft(WorkflowStep step) {
        if (step.getWorkflow().getStatus() != WorkflowStatus.DRAFT)
            throw new IllegalArgumentException("Chỉ được cấu hình step của workflow DRAFT");
    }
    private void validateHttpUrl(String value) {
        try {
            java.net.URI uri = java.net.URI.create(value == null ? "" : value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null)
                throw new IllegalArgumentException();
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("API action yêu cầu Endpoint URL HTTP(S) hợp lệ");
        }
    }
    private String write(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize step configuration"); } }
}
