package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.*;
import com.company.workflowbuilder.dto.response.*;
import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.entity.form.FormVersion;
import com.company.workflowbuilder.entity.user.*;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.exception.*;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.runtime.WorkflowActorResolver;
import com.company.workflowbuilder.service.runtime.WorkflowFieldValidationService;
import com.company.workflowbuilder.service.runtime.WorkflowJsonCodec;
import com.company.workflowbuilder.service.runtime.WorkflowSystemActionExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkflowEngineService {
    private final WorkflowRepository workflows;
    private final WorkflowStepRepository steps;
    private final WorkflowConnectionRepository connections;
    private final WorkflowInstanceRepository instances;
    private final WorkflowTaskRepository tasks;
    private final WorkflowBatchRecordRepository normalizedBatchRecords;
    private final WorkflowBatchRecordAccessRepository batchRecordAccess;
    private final SystemActionExecutionRepository systemActionExecutions;
    private final DatasetVersionRepository datasetVersions;
    private final RequestDraftRepository drafts;
    private final InstanceStepLogRepository logs;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final ConditionEvaluatorService evaluator;
    private final NotificationCenterService notificationCenter;
    private final NotificationStepDeliveryService notificationStepDelivery;
    private final WorkflowAuthorizationService workflowAuthorization;
    private final UserGroupRepository groupRepository;
    private final WorkflowJsonCodec codec;
    private final WorkflowFieldValidationService fieldValidation;
    private final WorkflowActorResolver actorResolver;
    private final WorkflowSystemActionExecutor systemActionExecutor;

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public InstanceResponse submit(CreateInstanceRequest request) {
        Workflow workflow = activeWorkflow(request.getWorkflowId());
        requireSubmitAccess(workflow);
        User requester = currentUser.user();
        WorkflowStep start = startStep(workflow);
        Map<String, Object> submittedFields = request.getFields() == null ? new HashMap<>()
                : new HashMap<>(request.getFields());
        WorkflowInstance instance = createInstance(workflow, start, requester, submittedFields, null, null);
        notificationCenter.create(requester, "Gửi yêu cầu thành công",
                instance.getRequestCode() + " đã được gửi và bắt đầu xử lý.", instance.getRequestCode(),
                "/instances/" + instance.getId());
        drafts.deleteByUserIdAndWorkflowFamilyId(requester.getId(), workflow.getFamilyId());
        return response(instances.save(instance));
    }

    @Transactional
    public BatchInstanceResponse submitBatch(CreateBatchInstanceRequest request) {
        Workflow workflow = activeWorkflow(request.getWorkflowId());
        requireSubmitAccess(workflow);
        User requester = currentUser.user();
        return submitBatchInternal(request, workflow, requester);
    }

    /** Trusted entry point used by a published data-pipeline binding. */
    @Transactional
    public BatchInstanceResponse submitBatchAs(UUID workflowId, List<Map<String, Object>> records, User requester) {
        CreateBatchInstanceRequest request = new CreateBatchInstanceRequest();
        request.setWorkflowId(workflowId);
        request.setRecords(records);
        return submitBatchInternal(request, activeWorkflow(workflowId), requester);
    }

    /** Applies pipeline revisions in-place until a person has acted. Once audit-relevant
     * human activity exists, the changed row is started as a new immutable revision. */
    @Transactional
    public BatchInstanceResponse submitPipelineBatchAs(UUID workflowId, List<Map<String, Object>> incoming,
            User requester) {
        Workflow workflow = activeWorkflow(workflowId);
        WorkflowStep start = startStep(workflow);
        List<Map<String, Object>> create = new ArrayList<>();
        Map<WorkflowInstance, List<PipelineUpdate>> updates = new LinkedHashMap<>();
        for (Map<String, Object> raw : incoming) {
            Map<String, Object> row = new LinkedHashMap<>(raw);
            String businessKey = Objects.toString(row.get("_pipelineBusinessKey"), "");
            Optional<WorkflowBatchRecord> previous = businessKey.isBlank() ? Optional.empty()
                    : normalizedBatchRecords.findFirstByInstanceWorkflowIdAndBusinessKeyOrderByRevisionDescUpdatedAtDesc(
                            workflow.getId(), businessKey);
            if (previous.isPresent() && previous.get().getHumanActionAt() == null
                    && InstanceStatus.RUNNING.name().equals(previous.get().getStatus())) {
                updates.computeIfAbsent(previous.get().getInstance(), key -> new ArrayList<>())
                        .add(new PipelineUpdate(previous.get(), row));
            } else {
                previous.ifPresent(value -> row.put("_pipelineRevision", value.getRevision() + 1));
                create.add(row);
            }
        }
        List<InstanceResponse> touched = new ArrayList<>();
        for (Map.Entry<WorkflowInstance, List<PipelineUpdate>> entry : updates.entrySet()) {
            WorkflowInstance instance = entry.getKey();
            List<Map<String, Object>> states = batchRecords(instance);
            Set<String> invalidActivations = new HashSet<>();
            for (PipelineUpdate update : entry.getValue()) {
                Map<String, Object> state = states.stream()
                        .filter(value -> ((Number) value.get("rowNumber")).intValue() == update.previous().getRowNumber())
                        .findFirst().orElseThrow();
                if (state.get("activationId") != null) invalidActivations.add(state.get("activationId").toString());
                Map<String, Object> fields = new LinkedHashMap<>(update.incoming());
                fields.keySet().removeIf(key -> key.startsWith("_pipeline"));
                fieldValidation.validateSubmission(start.getId(), fields);
                state.clear();
                state.put("rowNumber", update.previous().getRowNumber()); state.put("fields", fields);
                state.put("currentStepId", start.getId().toString()); state.put("currentStepLabel", start.getLabel());
                state.put("currentStepType", start.getType().name()); state.put("status", InstanceStatus.RUNNING.name());
                state.put("conditionBlocked", false); state.put("businessKey", update.incoming().get("_pipelineBusinessKey"));
                state.put("checksum", update.incoming().get("_pipelineChecksum"));
                state.put("sourceDatasetVersionId", update.incoming().get("_sourceDatasetVersionId"));
                state.put("revision", update.previous().getRevision());
                advanceBatchRecord(instance, state, start, null, 0);
            }
            if (!invalidActivations.isEmpty()) {
                tasks.findByInstanceIdAndStatus(instance.getId(), TaskStatus.PENDING).stream()
                        .filter(task -> task.getActivationId() != null && invalidActivations.contains(task.getActivationId().toString()))
                        .forEach(task -> { task.setStatus(TaskStatus.CANCELLED); task.setCompletedAt(LocalDateTime.now()); tasks.save(task); });
                states.stream().filter(state -> invalidActivations.contains(Objects.toString(state.get("activationId"), "")))
                        .forEach(state -> state.remove("activationId"));
            }
            saveBatchState(instance, states); synchronizeBatchTasks(instance, states); saveBatchState(instance, states);
            updateBatchSummary(instance, states); touched.add(response(instances.save(instance)));
        }
        if (!create.isEmpty()) return submitBatchAs(workflowId, create, requester);
        WorkflowInstance first = updates.keySet().stream().findFirst().orElseThrow();
        return BatchInstanceResponse.builder().batchId(first.getBatchId()).total(incoming.size()).instances(touched).build();
    }

    private BatchInstanceResponse submitBatchInternal(CreateBatchInstanceRequest request, Workflow workflow, User requester) {
        WorkflowStep start = startStep(workflow);
        Map<String, Object> startConfig = startConfig(workflow,start);
        if (!"BATCH".equals(startConfig.get("submissionMode")))
            throw new IllegalStateException("Workflow này chưa bật chế độ nộp danh sách");
        int limit = startConfig.get("maxBatchRows") instanceof Number number ? number.intValue() : 500;
        List<Map<String, Object>> records = request.getRecords() == null ? List.of() : request.getRecords();
        if (records.isEmpty())
            throw new IllegalArgumentException("Danh sách không có dữ liệu");
        if (records.size() > limit)
            throw new IllegalArgumentException("Danh sách vượt quá giới hạn " + limit + " dòng");

        UUID batchId = UUID.randomUUID();
        List<Map<String, Object>> batchRecords = new ArrayList<>();
        for (int index = 0; index < records.size(); index++) {
            try {
                Map<String, Object> record = records.get(index) == null
                        ? new HashMap<>() : new HashMap<>(records.get(index));
                Object pipelineBusinessKey = record.remove("_pipelineBusinessKey");
                Object pipelineChecksum = record.remove("_pipelineChecksum");
                Object sourceVersionId = record.remove("_sourceDatasetVersionId");
                Object pipelineRevision = record.remove("_pipelineRevision");
                fieldValidation.validateSubmission(start.getId(), record);
                Map<String, Object> state = new LinkedHashMap<>();
                state.put("rowNumber", index + 1);
                state.put("fields", record);
                state.put("currentStepId", start.getId().toString());
                state.put("currentStepLabel", start.getLabel());
                state.put("currentStepType", start.getType().name());
                state.put("status", InstanceStatus.RUNNING.name());
                state.put("conditionBlocked", false);
                if (pipelineBusinessKey != null) state.put("businessKey", pipelineBusinessKey);
                if (pipelineChecksum != null) state.put("checksum", pipelineChecksum);
                if (sourceVersionId != null) state.put("sourceDatasetVersionId", sourceVersionId);
                if (pipelineRevision != null) state.put("revision", pipelineRevision);
                batchRecords.add(state);
            } catch (RuntimeException ex) {
                String message = "Dòng " + (index + 1) + ": " + ex.getMessage();
                if (ex instanceof IllegalArgumentException)
                    throw new IllegalArgumentException(message, ex);
                throw new IllegalStateException(message, ex);
            }
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("_batch", true);
        envelope.put("records", batchRecords);
        WorkflowInstance instance = instances.save(WorkflowInstance.builder().workflow(workflow)
                .formVersion(requireForm(workflow)).createdBy(requester)
                .currentStep(start).requestCode(code()).batchId(batchId).fieldSnapshot(codec.write(envelope))
                .requesterWithdrawalAllowed(!Boolean.FALSE.equals(startConfig.get("allowRequesterWithdrawal"))).build());
        log(instance, start, requester, "BATCH_SUBMITTED", records.size() + " records");
        for (Map<String, Object> state : batchRecords)
            advanceBatchRecord(instance, state, start, null, 0);
        saveBatchState(instance, batchRecords);
        synchronizeBatchTasks(instance, batchRecords);
        saveBatchState(instance, batchRecords);
        updateBatchSummary(instance, batchRecords);
        InstanceResponse parent = response(instances.save(instance));
        notificationCenter.create(requester, "Đã gửi danh sách " + records.size() + " hồ sơ",
                "Batch " + batchId + " đã được đưa vào workflow.", instance.getRequestCode(),
                "/instances/" + instance.getId());
        drafts.deleteByUserIdAndWorkflowFamilyId(requester.getId(), workflow.getFamilyId());
        return BatchInstanceResponse.builder().batchId(batchId).total(records.size()).instances(List.of(parent)).build();
    }

    private record PipelineUpdate(WorkflowBatchRecord previous, Map<String, Object> incoming) {}

    private WorkflowInstance createInstance(Workflow workflow, WorkflowStep start, User requester,
            Map<String, Object> submittedFields, UUID batchId, Integer batchRowNumber) {
        fieldValidation.validateSubmission(start.getId(), submittedFields);
        WorkflowInstance instance = instances.save(WorkflowInstance.builder().workflow(workflow)
                .formVersion(requireForm(workflow)).createdBy(requester)
                .currentStep(start).requestCode(code()).batchId(batchId).batchRowNumber(batchRowNumber)
                .fieldSnapshot(codec.write(submittedFields))
                .requesterWithdrawalAllowed(!Boolean.FALSE.equals(startConfig(workflow,start).get("allowRequesterWithdrawal")))
                .build());
        log(instance, start, requester, "SUBMITTED", null);
        advance(instance, start, null, false, 0);
        return instances.save(instance);
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> requestDraft(UUID workflowId) {
        Workflow workflow = activeWorkflow(workflowId);
        requireSubmitAccess(workflow);
        return drafts.findByUserIdAndWorkflowFamilyId(currentUser.id(), workflow.getFamilyId())
                .map(draft -> draftResponse(draft, workflow));
    }

    @Transactional
    public Map<String, Object> saveRequestDraft(UUID workflowId, CreateInstanceRequest request) {
        if (request.getWorkflowId() != null && !workflowId.equals(request.getWorkflowId()))
            throw new IllegalArgumentException("Workflow trong URL và body không khớp");
        Workflow workflow = activeWorkflow(workflowId);
        requireSubmitAccess(workflow);
        WorkflowStep start = startStep(workflow);
        Map<String, Object> values = fieldValidation.sanitizeDraft(start.getId(), request.getFields());
        RequestDraft draft = drafts.findByUserIdAndWorkflowFamilyId(currentUser.id(), workflow.getFamilyId())
                .orElseGet(() -> RequestDraft.builder().user(currentUser.user())
                        .workflowFamilyId(workflow.getFamilyId()).build());
        draft.setWorkflowVersion(workflow);
        draft.setFormVersion(requireForm(workflow));
        draft.setFieldSnapshot(codec.write(values));
        return draftResponse(drafts.save(draft), workflow);
    }

    @Transactional
    public void deleteRequestDraft(UUID workflowId) {
        Workflow workflow = activeWorkflow(workflowId);
        drafts.deleteByUserIdAndWorkflowFamilyId(currentUser.id(), workflow.getFamilyId());
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public InstanceResponse act(UUID instanceId, String action, TaskActionRequest request) {
        WorkflowInstance instance = instance(instanceId);
        if (isBatch(instance))
            throw new IllegalArgumentException("Batch phải được xử lý qua task cụ thể");
        requireRunning(instance);
        WorkflowStep current = instance.getCurrentStep();
        User actor = currentUser.user();
        String normalizedAction = Objects.toString(action, "").toUpperCase(Locale.ROOT);
        validateTaskAction(current, normalizedAction);
        WorkflowTask task = tasks
                .findByInstanceIdAndStepIdAndAssigneeIdAndStatus(instanceId, current.getId(), actor.getId(),
                        TaskStatus.PENDING)
                .orElseThrow(() -> new AccessDeniedException("No active task is assigned to current user"));
        Map<String, Object> currentConfig = codec.stepConfig(current);
        String reviewComment = prepareReviewResults(task, request);
        if (current.getType() == StepType.REVIEW && Boolean.TRUE.equals(currentConfig.get("commentRequired"))
                && (request.getComment() == null || request.getComment().isBlank()))
            throw new IllegalArgumentException("Review comment is required");
        if (current.getType() == StepType.ASSIGNMENT && "FAIL".equals(normalizedAction)
                && (request.getComment() == null || request.getComment().isBlank()))
            throw new IllegalArgumentException("Lý do thất bại là bắt buộc");
        if (!"FAIL".equals(normalizedAction) && !"REJECT".equals(normalizedAction) && (current.getType() == StepType.REVIEW
                || current.getType() == StepType.APPROVAL || current.getType() == StepType.ASSIGNMENT)) {
            Map<String, Object> snapshot = codec.snapshot(instance.getFieldSnapshot());
            snapshot.putAll(fieldValidation.validateStepOutput(current.getId(), request.getFields()));
            instance.setFieldSnapshot(codec.write(snapshot));
        }
        if (current.getType() == StepType.APPROVAL && "REJECT".equals(normalizedAction)
                && (request.getComment() == null || request.getComment().isBlank()))
            throw new IllegalArgumentException("Lý do từ chối là bắt buộc");
        var calculatedRows = calculateTaskOutputs(task, request, List.of(codec.snapshot(instance.getFieldSnapshot())));
        if (!calculatedRows.isEmpty()) {
            var snapshot = codec.snapshot(instance.getFieldSnapshot());
            snapshot.putAll(calculatedRows.get(0));
            instance.setFieldSnapshot(codec.write(snapshot));
            reviewComment = Objects.toString(reviewComment, "") + "\nOutput tính toán: " + codec.write(calculatedRows);
        }
        if (current.getType() == StepType.REVIEW) {
            Map<String, Object> reviewedRecord = new LinkedHashMap<>();
            reviewedRecord.put("outcome", "REJECT".equals(normalizedAction) ? "FAIL" : "PASS");
            reviewedRecord.put("fields", codec.snapshot(instance.getFieldSnapshot()));
            storeReviewHandoff(task, normalizedAction, request, List.of(reviewedRecord));
        }
        if (current.getType() == StepType.REVIEW && "REJECT".equals(normalizedAction)) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            tasks.save(task);
            tasks.findByInstanceIdAndStatus(instanceId, TaskStatus.PENDING).stream()
                    .filter(other -> other.getStep().getId().equals(current.getId()))
                    .forEach(other -> {
                        other.setStatus(TaskStatus.CANCELLED);
                        tasks.save(other);
                    });
            log(instance, current, actor, "REVIEW_NOT_PASSED", reviewComment);
            List<WorkflowConnection> reviewConnections = connections.findByFromStepId(current.getId());
            ConnectionType failType = reviewConnections.stream()
                    .anyMatch(connection -> connection.getType() == ConnectionType.REVIEW_FAIL)
                            ? ConnectionType.REVIEW_FAIL
                            : reviewConnections.stream()
                                    .anyMatch(connection -> connection.getType() == ConnectionType.REJECT)
                                            ? ConnectionType.REJECT
                                            : null;
            if (failType != null) {
                advance(instance, current, failType, true, 0);
                notificationCenter.create(instance.getCreatedBy(), "Yêu cầu cần xử lý lại",
                        instance.getRequestCode() + " không đạt tại bước '" + current.getLabel()
                                + "' và đã chuyển tới bước '"
                                + instance.getCurrentStep().getLabel() + "'.",
                        instance.getRequestCode(), "/instances/" + instance.getId());
            } else {
                instance.setStatus(InstanceStatus.REJECTED);
                instance.setCompletedAt(LocalDateTime.now());
                instance.setConditionBlocked(false);
                instances.save(instance);
                notificationCenter.create(instance.getCreatedBy(), "Yêu cầu không đạt review",
                        instance.getRequestCode() + " đã kết thúc vì kết quả review không đạt.",
                        instance.getRequestCode(), "/instances/" + instance.getId());
            }
            return response(instances.save(instance));
        }
        if (current.getType() == StepType.ASSIGNMENT && "FAIL".equals(normalizedAction)) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setCompletedAt(LocalDateTime.now());
            tasks.save(task);
            cancelRemainingTasks(instanceId, current.getId());
            log(instance, current, actor, "ASSIGNMENT_FAILED", request.getComment());
            advance(instance, current, ConnectionType.ASSIGNMENT_FAIL, true, 0);
            return response(instances.save(instance));
        }
        ConnectionType desired = switch (normalizedAction) {
            case "APPROVE" -> ConnectionType.APPROVE;
            case "REJECT" -> ConnectionType.REJECT;
            default -> null;
        };
        if (current.getType() == StepType.REVIEW) {
            List<WorkflowConnection> reviewConnections = connections.findByFromStepId(current.getId());
            desired = reviewConnections.stream()
                    .anyMatch(connection -> connection.getType() == ConnectionType.REVIEW_PASS)
                            ? ConnectionType.REVIEW_PASS
                            : null; // DEFAULT remains the legacy pass branch.
        }
        if (current.getType() == StepType.ASSIGNMENT)
            desired = connections.findByFromStepId(current.getId()).stream()
                    .anyMatch(connection -> connection.getType() == ConnectionType.ASSIGNMENT_DONE)
                            ? ConnectionType.ASSIGNMENT_DONE : null;
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        tasks.save(task);
        log(instance, current, actor, normalizedAction, reviewComment);
        boolean successfulAction = !"REJECT".equals(normalizedAction);
        if (successfulAction && !completionThresholdReached(instanceId, current, task, currentConfig))
            return response(instances.save(instance));
        cancelRemainingTasks(instanceId, current.getId());
        advance(instance, current, desired, true, 0);
        return response(instances.save(instance));
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public InstanceResponse actTask(UUID taskId, String action, TaskActionRequest request) {
        WorkflowTask task = tasks.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", "id", taskId));
        if (task.getStatus() != TaskStatus.PENDING || !task.getAssignee().getId().equals(currentUser.id()))
            throw new AccessDeniedException("Task không được giao cho user hiện tại");
        if (!isBatch(task.getInstance()))
            return act(task.getInstance().getId(), action, request);
        return actBatchTask(task, action, request == null ? new TaskActionRequest() : request);
    }

    private InstanceResponse actBatchTask(WorkflowTask task, String action, TaskActionRequest request) {
        WorkflowInstance instance = task.getInstance();
        WorkflowStep step = task.getStep();
        String normalized = Objects.toString(action, "").toUpperCase(Locale.ROOT);
        validateTaskAction(step, normalized);
        Map<String, Object> config = codec.stepConfig(step);
        if (step.getType() == StepType.REVIEW && Boolean.TRUE.equals(config.get("commentRequired"))
                && (request.getComment() == null || request.getComment().isBlank()))
            throw new IllegalArgumentException("Review comment is required");
        if ((step.getType() == StepType.APPROVAL || step.getType() == StepType.REVIEW)
                && "REJECT".equals(normalized) && (request.getComment() == null || request.getComment().isBlank()))
            throw new IllegalArgumentException("Lý do từ chối là bắt buộc");
        if (step.getType() == StepType.ASSIGNMENT && "FAIL".equals(normalized)
                && (request.getComment() == null || request.getComment().isBlank()))
            throw new IllegalArgumentException("Lý do thất bại là bắt buộc");
        Map<String, Object> stepOutput = Map.of();
        if (!"FAIL".equals(normalized) && !"REJECT".equals(normalized)
                && (step.getType() == StepType.REVIEW || step.getType() == StepType.APPROVAL
                        || step.getType() == StepType.ASSIGNMENT))
            stepOutput = fieldValidation.validateStepOutput(step.getId(), request.getFields());
        List<Map<String, Object>> records = batchRecords(instance);
        List<Map<String, Object>> activationRecords = records.stream()
                .filter(record -> step.getId().toString().equals(record.get("currentStepId")))
                .filter(record -> task.getActivationId().toString().equals(record.get("activationId"))).toList();
        if (activationRecords.isEmpty())
            throw new IllegalStateException("Batch task không còn hồ sơ đang chờ xử lý");
        if (!stepOutput.isEmpty())
            for (Map<String, Object> record : activationRecords) {
                Map<String, Object> recordFields = codec.snapshot(codec.write(record.get("fields")));
                recordFields.putAll(stepOutput);
                record.put("fields", recordFields);
            }
        boolean rowLevelReview = step.getType() == StepType.REVIEW && request.getSelectedRowsOutcome() != null;
        Set<Integer> selectedRows = request.getSelectedRowNumbers() == null
                ? Set.of() : new HashSet<>(request.getSelectedRowNumbers());
        String selectedOutcome = Objects.toString(request.getSelectedRowsOutcome(), "").toUpperCase(Locale.ROOT);
        if (step.getType() == StepType.REVIEW && request.getSelectedRowNumbers() != null && !rowLevelReview)
            throw new IllegalArgumentException("Phải chọn kết quả PASS hoặc FAIL cho các dòng đã chọn");
        if (rowLevelReview && "COMMENT_ONLY".equals(config.get("resultMode")))
            throw new IllegalArgumentException("Review chỉ nhận xét không hỗ trợ phân loại PASS/FAIL theo dòng");
        if (rowLevelReview && !"COMPLETE".equals(normalized))
            throw new IllegalArgumentException("Phân loại từng dòng phải được gửi bằng thao tác hoàn thành review");
        if (rowLevelReview && !Set.of("PASS", "FAIL").contains(selectedOutcome))
            throw new IllegalArgumentException("Kết quả của dòng được chọn chỉ có thể là PASS hoặc FAIL");
        Set<Integer> availableRows = activationRecords.stream()
                .map(record -> Integer.parseInt(record.get("rowNumber").toString()))
                .collect(java.util.stream.Collectors.toSet());
        if (rowLevelReview && !availableRows.containsAll(selectedRows))
            throw new IllegalArgumentException("Danh sách dòng review chứa dòng không thuộc task hiện tại");
        long passCount = rowLevelReview ? activationRecords.stream().filter(record -> {
            boolean chosen = selectedRows.contains(Integer.parseInt(record.get("rowNumber").toString()));
            return chosen == "PASS".equals(selectedOutcome);
        }).count() : 0;
        if (rowLevelReview && passCount < activationRecords.size()
                && (request.getComment() == null || request.getComment().isBlank()))
            throw new IllegalArgumentException("Nhận xét là bắt buộc khi có dòng FAIL");
        String reviewComment = prepareReviewResults(task, request);
        var calculationInput = activationRecords.stream().map(record -> codec.snapshot(codec.write(record.get("fields")))).toList();
        var calculatedRows = calculateTaskOutputs(task, request, calculationInput);
        for (int index = 0; index < calculatedRows.size(); index++) {
            var row = calculationInput.get(index);
            row.putAll(calculatedRows.get(index));
            activationRecords.get(index).put("fields", row);
        }
        if (!calculatedRows.isEmpty())
            reviewComment = Objects.toString(reviewComment, "") + "\nOutput tính toán: " + codec.write(calculatedRows);
        if (step.getType() == StepType.REVIEW) {
            List<Map<String, Object>> handoffRecords = activationRecords.stream().map(record -> {
                Map<String, Object> reviewedRecord = new LinkedHashMap<>();
                reviewedRecord.put("rowNumber", record.get("rowNumber"));
                if (record.get("businessKey") != null) reviewedRecord.put("businessKey", record.get("businessKey"));
                boolean chosen = selectedRows.contains(Integer.parseInt(record.get("rowNumber").toString()));
                boolean passed = !rowLevelReview || chosen == "PASS".equals(selectedOutcome);
                reviewedRecord.put("outcome", passed && !"REJECT".equals(normalized) ? "PASS" : "FAIL");
                reviewedRecord.put("fields", codec.snapshot(codec.write(record.get("fields"))));
                return reviewedRecord;
            }).toList();
            storeReviewHandoff(task, normalized, request, handoffRecords);
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        tasks.save(task);
        String rowSummary = rowLevelReview
                ? passCount + " PASS, " + (activationRecords.size() - passCount) + " FAIL; " : "";
        log(instance, step, currentUser.user(), rowLevelReview ? "BATCH_REVIEW_ROWS" : "BATCH_" + normalized,
                rowSummary + activationRecords.size() + " records; " + Objects.toString(reviewComment, ""));
        boolean rejected = "REJECT".equals(normalized);
        boolean assignmentFailed = step.getType() == StepType.ASSIGNMENT && "FAIL".equals(normalized);
        if (!rejected && !assignmentFailed && !completionThresholdReached(instance.getId(), step, task, config)) {
            saveBatchState(instance, records);
            return response(instances.save(instance));
        }
        tasks.findByInstanceIdAndStepIdAndActivationId(instance.getId(), step.getId(), task.getActivationId())
                .stream().filter(other -> other.getStatus() == TaskStatus.PENDING).forEach(other -> {
                    other.setStatus(TaskStatus.CANCELLED);
                    other.setCompletedAt(LocalDateTime.now());
                    tasks.save(other);
                });
        List<WorkflowConnection> outgoing = connections.findByFromStepId(step.getId());
        ConnectionType desired = switch (step.getType()) {
            case APPROVAL -> rejected ? ConnectionType.REJECT : ConnectionType.APPROVE;
            case REVIEW -> rejected ? ConnectionType.REVIEW_FAIL
                    : outgoing.stream().anyMatch(connection -> connection.getType() == ConnectionType.REVIEW_PASS)
                            ? ConnectionType.REVIEW_PASS : null;
            case ASSIGNMENT -> assignmentFailed ? ConnectionType.ASSIGNMENT_FAIL
                    : outgoing.stream().anyMatch(connection -> connection.getType() == ConnectionType.ASSIGNMENT_DONE)
                            ? ConnectionType.ASSIGNMENT_DONE : null;
            default -> null;
        };
        boolean terminalReject = rejected && (step.getType() == StepType.REVIEW || step.getType() == StepType.APPROVAL)
                && outgoing.stream().noneMatch(connection -> connection.getType() == desired);
        for (Map<String, Object> record : activationRecords) {
            record.put("humanActionAt", LocalDateTime.now().toString());
            ConnectionType recordDesired = desired;
            boolean recordTerminalReject = terminalReject;
            if (rowLevelReview) {
                boolean chosen = selectedRows.contains(Integer.parseInt(record.get("rowNumber").toString()));
                boolean passed = chosen == "PASS".equals(selectedOutcome);
                recordDesired = passed
                        ? outgoing.stream().anyMatch(connection -> connection.getType() == ConnectionType.REVIEW_PASS)
                                ? ConnectionType.REVIEW_PASS : null
                        : ConnectionType.REVIEW_FAIL;
                recordTerminalReject = !passed && outgoing.stream()
                        .noneMatch(connection -> connection.getType() == ConnectionType.REVIEW_FAIL);
                markBatchOutcome(record, passed ? "PASS" : "FAIL", request.getComment(), currentUser.user());
            } else {
                String outcome = rejected ? "REJECT" : assignmentFailed ? "FAIL"
                        : step.getType() == StepType.REVIEW ? "PASS" : normalized;
                markBatchOutcome(record, outcome, request.getComment(), currentUser.user());
            }
            if (recordTerminalReject) {
                record.put("status", InstanceStatus.REJECTED.name());
                record.put("conditionBlocked", false);
                record.put("completedAt", LocalDateTime.now().toString());
            } else advanceBatchRecord(instance, record, step, recordDesired, 0);
        }
        saveBatchState(instance, records);
        synchronizeBatchTasks(instance, records);
        saveBatchState(instance, records);
        updateBatchSummary(instance, records);
        return response(instances.save(instance));
    }

    @Transactional(noRollbackFor = BusinessRuleException.class)
    public InstanceResponse reEvaluate(UUID instanceId) {
        WorkflowInstance instance = instance(instanceId);
        requireReEvaluateAccess(instance);
        if (isBatch(instance)) {
            List<Map<String, Object>> records = batchRecords(instance);
            for (Map<String, Object> record : records) {
                if (!InstanceStatus.RUNNING.name().equals(record.get("status"))
                        || !Boolean.TRUE.equals(record.get("conditionBlocked"))) continue;
                UUID stepId = UUID.fromString(record.get("currentStepId").toString());
                WorkflowStep step = steps.findById(stepId)
                        .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", stepId));
                advanceBatchRecord(instance, record, step, null, 0);
            }
            saveBatchState(instance, records);
            synchronizeBatchTasks(instance, records);
            saveBatchState(instance, records);
            updateBatchSummary(instance, records);
            return response(instances.save(instance));
        }
        advance(instance, instance.getCurrentStep(), null, true, 0);
        return response(instances.save(instance));
    }

    @Transactional
    public InstanceResponse cancel(UUID instanceId) {
        WorkflowInstance instance = instance(instanceId);
        requireRunning(instance);
        if (!currentUser.hasRole(SystemRole.ADMIN) && !instance.getCreatedBy().getId().equals(currentUser.id()))
            throw new AccessDeniedException("Only requester or admin can cancel");
        instance.setStatus(InstanceStatus.CANCELLED);
        if (isBatch(instance)) terminateBatchRecords(instance, InstanceStatus.CANCELLED,
                currentUser.user(), "Batch đã bị hủy bởi " + currentUser.user().getDisplayName());
        instance.setCompletedAt(LocalDateTime.now());
        tasks.findByInstanceIdAndStatus(instanceId, TaskStatus.PENDING).forEach(t -> {
            t.setStatus(TaskStatus.CANCELLED);
            tasks.save(t);
        });
        log(instance, instance.getCurrentStep(), currentUser.user(), "CANCELLED", null);
        notificationCenter.create(instance.getCreatedBy(), "Yêu cầu đã bị hủy",
                instance.getRequestCode() + " đã kết thúc với trạng thái Đã hủy.", instance.getRequestCode(),
                "/instances/" + instance.getId());
        return response(instances.save(instance));
    }

    @Transactional
    public InstanceResponse withdraw(UUID instanceId, TaskActionRequest request) {
        WorkflowInstance instance = instance(instanceId);
        requireRunning(instance);
        if (!instance.getCreatedBy().getId().equals(currentUser.id()))
            throw new AccessDeniedException("Chỉ người tạo request mới được thu hồi");
        if (!instance.isRequesterWithdrawalAllowed())
            throw new AccessDeniedException("Workflow này không cho phép người gửi thu hồi yêu cầu");
        String reason = request == null ? "" : Objects.toString(request.getComment(), "").trim();
        if (reason.isBlank())
            throw new IllegalArgumentException("Lý do thu hồi là bắt buộc");
        if (reason.length() > 1000)
            throw new IllegalArgumentException("Lý do thu hồi không được vượt quá 1000 ký tự");

        List<WorkflowTask> pendingTasks = tasks.findByInstanceIdAndStatus(instanceId, TaskStatus.PENDING);
        LinkedHashMap<UUID, User> recipients = new LinkedHashMap<>();
        pendingTasks.forEach(task -> recipients.put(task.getAssignee().getId(), task.getAssignee()));
        User owner = instance.getWorkflow().getOwner();
        recipients.put(owner.getId(), owner);
        recipients.remove(instance.getCreatedBy().getId());

        LocalDateTime now = LocalDateTime.now();
        pendingTasks.forEach(task -> {
            task.setStatus(TaskStatus.CANCELLED);
            task.setCompletedAt(now);
            tasks.save(task);
        });
        instance.setStatus(InstanceStatus.WITHDRAWN);
        if (isBatch(instance)) terminateBatchRecords(instance, InstanceStatus.WITHDRAWN,
                currentUser.user(), reason);
        instance.setConditionBlocked(false);
        instance.setCompletedAt(now);
        instance.setWithdrawnAt(now);
        instance.setWithdrawnBy(currentUser.user());
        instance.setWithdrawalReason(reason);
        log(instance, instance.getCurrentStep(), currentUser.user(), "REQUEST_WITHDRAWN", reason);
        recipients.values().stream().filter(User::isActive).forEach(user -> notificationCenter.create(user,
                "Yêu cầu đã được thu hồi",
                instance.getRequestCode() + " đã được người gửi thu hồi tại bước '"
                        + instance.getCurrentStep().getLabel() + "'.",
                instance.getRequestCode(), "/instances/" + instance.getId()));
        notificationCenter.create(instance.getCreatedBy(), "Thu hồi yêu cầu thành công",
                instance.getRequestCode() + " đã dừng xử lý.", instance.getRequestCode(),
                "/instances/" + instance.getId());
        return response(instances.save(instance));
    }

    @Transactional(readOnly = true)
    public List<InstanceResponse> mine() {
        return instances.findByCreatedByIdOrderByStartedAtDesc(currentUser.id()).stream()
                .map(this::instanceSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<InstanceResponse> accessibleInstances() {
        if (currentUser.hasRole(SystemRole.ADMIN))
            return instances.findAllByOrderByStartedAtDesc().stream().map(this::response).toList();
        if (currentUser.hasRole(SystemRole.WORKFLOW_OWNER))
            return instances.findAllByOrderByStartedAtDesc().stream()
                    .filter(instance -> instance.getWorkflow().getOwner().getId().equals(currentUser.id()))
                    .map(this::response).toList();
        Set<UUID> taskInstances = tasks
                .findByAssigneeIdOrderByCreatedAtDesc(currentUser.id()).stream()
                .map(task -> task.getInstance().getId()).collect(java.util.stream.Collectors.toSet());
        return instances.findAllByOrderByStartedAtDesc().stream()
                .filter(instance -> instance.getCreatedBy().getId().equals(currentUser.id())
                        || instance.getWorkflow().getOwner().getId().equals(currentUser.id())
                        || taskInstances.contains(instance.getId())
                        || canViewAsRecordRecipient(instance, currentUser.id()))
                .map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public InstanceResponse get(UUID id) {
        WorkflowInstance i = instance(id);
        requireRuntimeAccess(i);
        return response(i);
    }

    @Transactional(readOnly = true)
    public List<InstanceHistoryResponse> history(UUID id) {
        WorkflowInstance i = instance(id);
        requireRuntimeAccess(i);
        var history = logs.findByInstanceIdOrderByActedAtAsc(id);
        boolean fullHistory = currentUser.hasRole(SystemRole.ADMIN)
                || i.getCreatedBy().getId().equals(currentUser.id())
                || i.getWorkflow().getOwner().getId().equals(currentUser.id());
        if (!fullHistory) {
            Set<UUID> assignedStepIds = tasks.findByInstanceIdAndAssigneeId(id, currentUser.id()).stream()
                    .map(task -> task.getStep().getId()).collect(java.util.stream.Collectors.toSet());
            history = history.stream().filter(item -> assignedStepIds.contains(item.getStep().getId())).toList();
        }
        return history.stream()
                .map(log -> InstanceHistoryResponse.builder().id(log.getId()).stepId(log.getStep().getId())
                        .stepLabel(log.getStep().getLabel()).stepType(log.getStep().getType().name())
                        .action(log.getAction()).actorId(log.getActor() == null ? null : log.getActor().getId())
                        .actorName(log.getActor() == null ? "SYSTEM" : log.getActor().getDisplayName())
                        .comment(log.getComment()).actedAt(log.getActedAt()).build())
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> batchRecords(UUID id, int page, int size) {
        WorkflowInstance instance = instance(id);
        requireRuntimeAccess(instance);
        if (!isBatch(instance)) throw new IllegalArgumentException("Instance không phải batch");
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(200, size));
        List<WorkflowBatchRecord> latest = latestBatchRecordEntities(instance.getId());
        List<Map<String, Object>> records = latest.isEmpty()
                ? batchRecords(instance).stream().filter(state -> canViewBatchState(instance, state))
                        .map(this::legacyBatchSummary).toList()
                : latest.stream().filter(row -> canViewBatchRecord(instance, row))
                        .map(this::batchRecordSummary).toList();
        int from = Math.min(records.size(), safePage * safeSize);
        int to = Math.min(records.size(), from + safeSize);
        Map<String, Long> statusCounts = records.stream().collect(java.util.stream.Collectors.groupingBy(
                record -> Objects.toString(record.get("status"), InstanceStatus.RUNNING.name()),
                LinkedHashMap::new, java.util.stream.Collectors.counting()));
        return Map.of("items", records.subList(from, to), "page", safePage, "size", safeSize,
                "total", records.size(), "totalPages", (records.size() + safeSize - 1) / safeSize,
                "statusCounts", statusCounts);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> batchRecord(UUID id, int rowNumber) {
        WorkflowInstance instance = instance(id);
        requireRuntimeAccess(instance);
        if (!isBatch(instance)) throw new IllegalArgumentException("Instance không phải batch");
        WorkflowBatchRecord row = normalizedBatchRecords
                .findFirstByInstanceIdAndRowNumberOrderByRevisionDesc(id, rowNumber).orElse(null);
        Map<String, Object> state;
        Map<String, Object> result;
        if (row != null) {
            if (!canViewBatchRecord(instance, row)) throw new AccessDeniedException("Cannot view this batch row");
            state = codec.snapshot(row.getStateJson());
            result = new LinkedHashMap<>(batchRecordSummary(row));
            result.put("fields", codec.snapshot(row.getPayloadJson()));
        } else {
            state = batchRecords(instance).stream()
                    .filter(value -> Objects.equals(((Number) value.get("rowNumber")).intValue(), rowNumber))
                    .findFirst().orElseThrow(() -> new ResourceNotFoundException("WorkflowBatchRecord", "rowNumber", rowNumber));
            if (!canViewBatchState(instance, state)) throw new AccessDeniedException("Cannot view this batch row");
            result = new LinkedHashMap<>(legacyBatchSummary(state));
            result.put("fields", state.getOrDefault("fields", Map.of()));
        }
        result.put("conditionBlocked", Boolean.TRUE.equals(state.get("conditionBlocked")));
        result.put("historicalReasonAvailable", result.get("lastReason") != null);
        result.put("systemActions", batchSystemActions(instance, rowNumber));
        return result;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> myTasks(TaskStatus status) {
        if (status != TaskStatus.PENDING && status != TaskStatus.COMPLETED)
            throw new IllegalArgumentException("Chỉ hỗ trợ xem task đang chờ hoặc đã hoàn thành");
        return tasks.findByAssigneeIdAndStatusOrderByCreatedAtDesc(currentUser.id(), status).stream()
                .map(this::taskSummaryResponse).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse myTask(UUID taskId) {
        WorkflowTask task = tasks.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", "id", taskId));
        if (!task.getAssignee().getId().equals(currentUser.id()))
            throw new AccessDeniedException("Task không được giao cho user hiện tại");
        return taskResponse(task);
    }

    private void advance(WorkflowInstance instance, WorkflowStep source, ConnectionType desired, boolean throwIfBlocked,
            int depth) {
        if (depth > 50)
            throw new IllegalStateException("Workflow automatic transition limit exceeded");
        List<WorkflowConnection> outgoing = connections.findByFromStepId(source.getId());
        WorkflowConnection selected = select(outgoing, desired, codec.snapshot(instance.getFieldSnapshot()));
        if (selected == null) {
            instance.setConditionBlocked(true);
            instances.save(instance);
            if (throwIfBlocked)
                throw new BusinessRuleException("CONDITION_NOT_SATISFIED",
                        "Request remains at step '" + source.getLabel() + "'");
            return;
        }
        WorkflowStep next = selected.getToStep();
        instance.setCurrentStep(next);
        instance.setConditionBlocked(false);
        instances.save(instance);
        log(instance, next, null, "STEP_ACTIVATED", null);
        if (next.getType() == StepType.END) {
            Map<String, Object> endConfig = codec.stepConfig(next);
            String fallback = desired == ConnectionType.REJECT || desired == ConnectionType.REVIEW_FAIL ? "REJECTED"
                    : "COMPLETED";
            try {
                instance.setStatus(InstanceStatus.valueOf(Objects.toString(endConfig.get("outcome"), fallback)));
            } catch (IllegalArgumentException ignored) {
                instance.setStatus(InstanceStatus.COMPLETED);
            }
            instance.setCompletedAt(LocalDateTime.now());
            if (!Boolean.FALSE.equals(endConfig.get("notifyRequester"))) {
                String title = codec.render(Objects.toString(endConfig.get("title"), "Request đã kết thúc"), instance,
                        codec.snapshot(instance.getFieldSnapshot()));
                String body = codec.render(
                        Objects.toString(endConfig.get("message"),
                                instance.getRequestCode() + " - " + instance.getStatus()),
                        instance, codec.snapshot(instance.getFieldSnapshot()));
                notificationCenter.create(instance.getCreatedBy(), title, body, instance.getRequestCode(),
                        "/instances/" + instance.getId());
            }
            if (Boolean.TRUE.equals(endConfig.get("notifyRecordRecipient"))) {
                User requester = instance.getCreatedBy();
                recordRecipient(instance).filter(user -> !user.getId().equals(requester.getId()))
                        .ifPresent(user -> {
                            String title = codec.render(Objects.toString(endConfig.get("title"), "Workflow đã kết thúc"),
                                    instance, codec.snapshot(instance.getFieldSnapshot()));
                            String body = codec.render(Objects.toString(endConfig.get("message"),
                                    instance.getRequestCode() + " - " + instance.getStatus()), instance,
                                    codec.snapshot(instance.getFieldSnapshot()));
                            notificationCenter.create(user, title, body, instance.getRequestCode(),
                                    "/instances/" + instance.getId());
                        });
            }
            return;
        }
        if (next.getType() == StepType.NOTIFICATION) {
            customNotification(instance, next, notificationTrigger(source, desired));
            advance(instance, next, null, true, depth + 1);
            return;
        }
        if (next.getType() == StepType.SYSTEM_ACTION) {
            var executionOutcome = systemActionExecutor.executeOutcome(instance, next,
                    List.of(codec.snapshot(instance.getFieldSnapshot())), null);
            if (executionOutcome == WorkflowSystemActionExecutor.Outcome.QUEUED) return;
            boolean success = executionOutcome == WorkflowSystemActionExecutor.Outcome.SUCCESS;
            ConnectionType outcome = systemActionRoute(next, success);
            boolean explicit = outcome != null && connections.findByFromStepId(next.getId()).stream()
                    .anyMatch(connection -> connection.getType() == outcome);
            if (!success && !explicit && !"CONTINUE".equals(codec.stepConfig(next).get("failurePolicy")))
                throw new IllegalStateException("System Action failed and has no SYSTEM_FAIL branch");
            advance(instance, next, explicit ? outcome : null, true, depth + 1);
            return;
        }
        if (next.getType() == StepType.START) {
            advance(instance, next, null, true, depth + 1);
            return;
        }
        if (next.getType() == StepType.APPROVAL
                && "AUTO".equalsIgnoreCase(Objects.toString(codec.stepConfig(next).get("mode"), ""))) {
            boolean approved = autoMatches(codec.stepConfig(next), codec.snapshot(instance.getFieldSnapshot()));
            log(instance, next, null, approved ? "AUTO_APPROVED" : "AUTO_REJECTED", null);
            advance(instance, next, approved ? ConnectionType.APPROVE : ConnectionType.REJECT, true, depth + 1);
            return;
        }
        createTasks(instance, next);
    }

    @SuppressWarnings("unchecked")
    private void advanceBatchRecord(WorkflowInstance instance, Map<String, Object> state, WorkflowStep source,
            ConnectionType desired, int depth) {
        if (depth > 50) throw new IllegalStateException("Workflow automatic transition limit exceeded");
        Map<String, Object> fields = (Map<String, Object>) state.getOrDefault("fields", new LinkedHashMap<>());
        WorkflowConnection selected = select(connections.findByFromStepId(source.getId()), desired, fields);
        if (selected == null) {
            state.put("conditionBlocked", true);
            markBatchOutcome(state, "CONDITION_BLOCKED", "Không có điều kiện chuyển bước nào thỏa mãn", null);
            return;
        }
        WorkflowStep next = selected.getToStep();
        state.put("currentStepId", next.getId().toString());
        state.put("currentStepLabel", next.getLabel());
        state.put("currentStepType", next.getType().name());
        state.put("conditionBlocked", false);
        state.remove("activationId");
        state.remove("actorIds");
        log(instance, next, null, "BATCH_ROW_STEP_ACTIVATED", "row=" + state.get("rowNumber"));
        if (next.getType() == StepType.END) {
            Map<String, Object> config = codec.stepConfig(next);
            String fallback = desired == ConnectionType.REJECT || desired == ConnectionType.REVIEW_FAIL
                    ? "REJECTED" : "COMPLETED";
            state.put("status", Objects.toString(config.get("outcome"), fallback));
            state.put("completedAt", LocalDateTime.now().toString());
            if (state.get("lastOutcome") == null)
                markBatchOutcome(state, Objects.toString(state.get("status"), "COMPLETED"), null, null);
            if (Boolean.TRUE.equals(config.get("notifyRecordRecipient")))
                notifyBatchRecordAtEnd(instance, fields, config);
            return;
        }
        if (next.getType() == StepType.NOTIFICATION) {
            withRecordSnapshot(instance, fields,
                    () -> customNotification(instance, next, notificationTrigger(source, desired)));
            advanceBatchRecord(instance, state, next, null, depth + 1);
            return;
        }
        if (next.getType() == StepType.SYSTEM_ACTION) {
            String original = instance.getFieldSnapshot();
            List<Map<String, Object>> calculationRows = new ArrayList<>();
            calculationRows.add(new LinkedHashMap<>(fields));
            batchRecords(instance).stream()
                    .filter(record -> !Objects.equals(record.get("rowNumber"), state.get("rowNumber")))
                    .map(record -> codec.snapshot(codec.write(record.get("fields"))))
                    .forEach(calculationRows::add);
            instance.setFieldSnapshot(codec.write(fields));
            var executionOutcome = systemActionExecutor.executeOutcome(instance, next, calculationRows,
                    ((Number) state.get("rowNumber")).intValue());
            Map<String, Object> updated = codec.snapshot(instance.getFieldSnapshot());
            instance.setFieldSnapshot(original);
            fields.clear();
            fields.putAll(updated);
            state.put("fields", fields);
            if (executionOutcome == WorkflowSystemActionExecutor.Outcome.QUEUED) return;
            boolean success = executionOutcome == WorkflowSystemActionExecutor.Outcome.SUCCESS;
            markBatchOutcome(state, success ? "SYSTEM_SUCCESS" : "SYSTEM_FAIL",
                    success ? null : "System Action thất bại", null);
            ConnectionType outcome = systemActionRoute(next, success);
            boolean explicit = outcome != null && connections.findByFromStepId(next.getId()).stream()
                    .anyMatch(connection -> connection.getType() == outcome);
            if (!success && !explicit && !"CONTINUE".equals(codec.stepConfig(next).get("failurePolicy")))
                throw new IllegalStateException("System Action failed and has no SYSTEM_FAIL branch");
            advanceBatchRecord(instance, state, next, explicit ? outcome : null, depth + 1);
            return;
        }
        if (next.getType() == StepType.START) {
            advanceBatchRecord(instance, state, next, null, depth + 1);
            return;
        }
        if (next.getType() == StepType.APPROVAL
                && "AUTO".equalsIgnoreCase(Objects.toString(codec.stepConfig(next).get("mode"), ""))) {
            boolean approved = autoMatches(codec.stepConfig(next), fields);
            markBatchOutcome(state, approved ? "AUTO_APPROVED" : "AUTO_REJECTED",
                    approved ? null : "Không thỏa điều kiện tự động phê duyệt", null);
            advanceBatchRecord(instance, state, next,
                    approved ? ConnectionType.APPROVE : ConnectionType.REJECT, depth + 1);
            return;
        }
        String original = instance.getFieldSnapshot();
        instance.setFieldSnapshot(codec.write(fields));
        List<User> actors;
        try { actors = actorResolver.resolve(instance, codec.stepConfig(next)); }
        finally { instance.setFieldSnapshot(original); }
        if (actors.isEmpty()) throw new IllegalStateException("Step '" + next.getLabel() + "' cannot resolve an active actor");
        state.put("actorIds", actors.stream().map(user -> user.getId().toString()).sorted().toList());
    }

    /** Resumes an instance after a durable connector execution has reached a terminal state. */
    @Transactional
    public void resumeSystemAction(SystemActionExecution execution, boolean success, Map<String, Object> outputs) {
        WorkflowInstance instance = instances.findById(execution.getInstance().getId())
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", "id", execution.getInstance().getId()));
        WorkflowStep step = steps.findById(execution.getStep().getId())
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", execution.getStep().getId()));
        ConnectionType outcome = systemActionRoute(step, success);
        if (execution.getBatchRowNumber() != null) {
            List<Map<String, Object>> records = batchRecords(instance);
            Map<String, Object> state = records.stream()
                    .filter(record -> Objects.equals(((Number) record.get("rowNumber")).intValue(), execution.getBatchRowNumber()))
                    .findFirst().orElseThrow(() -> new IllegalStateException("System Action batch row no longer exists"));
            if (!step.getId().toString().equals(Objects.toString(state.get("currentStepId"), ""))) return;
            log(instance, step, null, success ? "SYSTEM_ACTION_COMPLETED" : "SYSTEM_ACTION_FAILED",
                    safeSystemActionComment(execution, success));
            @SuppressWarnings("unchecked")
            Map<String, Object> fields = (Map<String, Object>) state.getOrDefault("fields", new LinkedHashMap<>());
            if (success) fields.putAll(outputs);
            state.put("fields", fields);
            markBatchOutcome(state, success ? "SYSTEM_SUCCESS" : "SYSTEM_FAIL",
                    success ? null : "System Action thất bại (execution " + execution.getId() + ")", null);
            advanceBatchRecord(instance, state, step, outcome, 0);
            saveBatchState(instance, records);
            synchronizeBatchTasks(instance, records);
            saveBatchState(instance, records);
            updateBatchSummary(instance, records);
            instances.save(instance);
            return;
        }
        if (instance.getCurrentStep() == null || !instance.getCurrentStep().getId().equals(step.getId())) return;
        log(instance, step, null, success ? "SYSTEM_ACTION_COMPLETED" : "SYSTEM_ACTION_FAILED",
                safeSystemActionComment(execution, success));
        if (success && !outputs.isEmpty()) {
            Map<String, Object> snapshot = codec.snapshot(instance.getFieldSnapshot());
            snapshot.putAll(outputs);
            instance.setFieldSnapshot(codec.write(snapshot));
            instances.save(instance);
        }
        advance(instance, step, outcome, true, 0);
        instances.save(instance);
    }

    /** A successful System Action may evaluate IF/ELSE against its newly mapped snapshot fields. */
    private ConnectionType systemActionRoute(WorkflowStep step, boolean success) {
        if (!success) return ConnectionType.SYSTEM_FAIL;
        boolean conditional = connections.findByFromStepId(step.getId()).stream()
                .anyMatch(connection -> connection.getType() == ConnectionType.IF);
        return conditional ? null : ConnectionType.SYSTEM_SUCCESS;
    }

    @SuppressWarnings("unchecked")
    private void synchronizeBatchTasks(WorkflowInstance instance, List<Map<String, Object>> records) {
        Map<String, List<Map<String, Object>>> groups = records.stream()
                .filter(record -> InstanceStatus.RUNNING.name().equals(record.get("status")))
                .filter(record -> record.get("actorIds") instanceof Collection<?> actors && !actors.isEmpty())
                .collect(java.util.stream.Collectors.groupingBy(record -> record.get("currentStepId") + "|"
                        + String.join(",", (List<String>) record.get("actorIds")), LinkedHashMap::new,
                        java.util.stream.Collectors.toList()));
        List<WorkflowTask> pending = tasks.findByInstanceIdAndStatus(instance.getId(), TaskStatus.PENDING);
        for (List<Map<String, Object>> group : groups.values()) {
            Map<String, Object> first = group.get(0);
            UUID stepId = UUID.fromString(first.get("currentStepId").toString());
            WorkflowStep step = steps.findById(stepId)
                    .orElseThrow(() -> new ResourceNotFoundException("WorkflowStep", "id", stepId));
            List<String> actorIds = (List<String>) first.get("actorIds");
            boolean alreadyActive = group.stream().map(record -> Objects.toString(record.get("activationId"), ""))
                    .anyMatch(value -> !value.isBlank());
            if (alreadyActive) continue;
            UUID activationId = UUID.randomUUID();
            group.forEach(record -> record.put("activationId", activationId.toString()));
            Map<String, Object> config = codec.stepConfig(step);
            Integer hours = config.get("deadlineHours") instanceof Number number ? number.intValue() : null;
            LocalDateTime deadline = hours == null ? null : LocalDateTime.now().plusHours(hours);
            for (String actorId : actorIds) {
                UUID userId = UUID.fromString(actorId);
                User assignee = users.findById(userId).filter(User::isActive)
                        .orElseThrow(() -> new IllegalStateException("Batch actor is no longer active: " + userId));
                if (pending.stream().anyMatch(task -> task.getStep().getId().equals(stepId)
                        && task.getAssignee().getId().equals(userId)
                        && activationId.equals(task.getActivationId()))) continue;
                WorkflowTask task = tasks.save(WorkflowTask.builder().instance(instance).step(step).assignee(assignee)
                        .activationId(activationId).deadlineAt(deadline).build());
                notificationCenter.create(assignee, "Bạn có batch task mới",
                        instance.getRequestCode() + " - " + group.size() + " hồ sơ - " + step.getLabel(),
                        instance.getRequestCode(), "/tasks/" + task.getId());
            }
        }
    }

    private void saveBatchState(WorkflowInstance instance, List<Map<String, Object>> records) {
        instance.setFieldSnapshot(codec.write(Map.of("_batch", true, "records", records)));
        instances.save(instance);
        for (Map<String, Object> state : records) {
            int rowNumber = ((Number) state.get("rowNumber")).intValue();
            WorkflowBatchRecord row = normalizedBatchRecords
                    .findFirstByInstanceIdAndRowNumberOrderByRevisionDesc(instance.getId(), rowNumber)
                    .orElseGet(() -> WorkflowBatchRecord.builder().instance(instance).rowNumber(rowNumber).revision(1).build());
            Object fields = state.get("fields");
            row.setPayloadJson(codec.write(fields instanceof Map<?, ?> map ? map : Map.of()));
            row.setStateJson(codec.write(state));
            row.setStatus(Objects.toString(state.get("status"), InstanceStatus.RUNNING.name()));
            row.setBusinessKey(Objects.toString(state.get("businessKey"), null));
            row.setChecksum(Objects.toString(state.get("checksum"), null));
            if (state.get("revision") instanceof Number revision) row.setRevision(revision.intValue());
            Object sourceVersionId = state.get("sourceDatasetVersionId");
            row.setSourceDatasetVersion(sourceVersionId == null ? null
                    : datasetVersions.findById(UUID.fromString(sourceVersionId.toString())).orElse(null));
            Object stepId = state.get("currentStepId");
            row.setCurrentStep(stepId == null ? null : steps.findById(UUID.fromString(stepId.toString())).orElse(null));
            Object acted = state.get("humanActionAt");
            row.setHumanActionAt(acted == null ? null : LocalDateTime.parse(acted.toString()));
            row.setLastOutcome(Objects.toString(state.get("lastOutcome"), null));
            row.setLastReason(limitReason(Objects.toString(state.get("lastReason"), null)));
            Object lastActorId = state.get("lastActorId");
            row.setLastActor(lastActorId == null ? null
                    : users.findById(UUID.fromString(lastActorId.toString())).orElse(null));
            row.setLastActionAt(parseDateTime(state.get("lastActionAt")));
            row.setCompletedAt(parseDateTime(state.get("completedAt")));
            row = normalizedBatchRecords.save(row);
            grantBatchRecordAccess(row, state, fields);
        }
    }

    private void terminateBatchRecords(WorkflowInstance instance, InstanceStatus status, User actor, String reason) {
        List<Map<String, Object>> records = batchRecords(instance);
        records.stream().filter(record -> InstanceStatus.RUNNING.name().equals(record.get("status"))).forEach(record -> {
            record.put("status", status.name());
            record.put("conditionBlocked", false);
            record.put("completedAt", LocalDateTime.now().toString());
            markBatchOutcome(record, status.name(), reason, actor);
            record.remove("activationId");
            record.remove("actorIds");
        });
        saveBatchState(instance, records);
    }

    private void markBatchOutcome(Map<String, Object> state, String outcome, String reason, User actor) {
        state.put("lastOutcome", outcome);
        if (reason == null || reason.isBlank()) state.remove("lastReason");
        else state.put("lastReason", limitReason(reason));
        if (actor == null) state.remove("lastActorId");
        else state.put("lastActorId", actor.getId().toString());
        state.put("lastActionAt", LocalDateTime.now().toString());
    }

    private void grantBatchRecordAccess(WorkflowBatchRecord row, Map<String, Object> state, Object fields) {
        if (row.getId() == null) return;
        if (state.get("actorIds") instanceof Collection<?> actorIds)
            for (Object actorId : actorIds) grantBatchRecordAccess(row, actorId, "ACTOR");
        if (fields instanceof Map<?, ?> values) {
            @SuppressWarnings("unchecked")
            Map<String, Object> recordFields = (Map<String, Object>) values;
            recordRecipient(row.getInstance(), recordFields)
                    .ifPresent(user -> grantBatchRecordAccess(row, user.getId(), "RECORD_RECIPIENT"));
        }
    }

    private void grantBatchRecordAccess(WorkflowBatchRecord row, Object rawUserId, String type) {
        try {
            UUID userId = rawUserId instanceof UUID id ? id : UUID.fromString(rawUserId.toString());
            if (batchRecordAccess.existsByBatchRecordIdAndUserIdAndAccessType(row.getId(), userId, type)) return;
            users.findById(userId).ifPresent(user -> batchRecordAccess.save(WorkflowBatchRecordAccess.builder()
                    .batchRecord(row).user(user).accessType(type).build()));
        } catch (IllegalArgumentException ignored) {
        }
    }

    private LocalDateTime parseDateTime(Object value) {
        if (value == null || value.toString().isBlank()) return null;
        try { return LocalDateTime.parse(value.toString()); }
        catch (RuntimeException ignored) { return null; }
    }

    private String limitReason(String value) {
        if (value == null || value.isBlank()) return null;
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }

    private String safeSystemActionComment(SystemActionExecution execution, boolean success) {
        String comment = "execution=" + execution.getId();
        if (!success) comment += "; System Action thất bại"
                + (execution.getResponseStatus() == null ? "" : " (HTTP " + execution.getResponseStatus() + ")");
        return comment;
    }

    private void updateBatchSummary(WorkflowInstance instance, List<Map<String, Object>> records) {
        List<Map<String, Object>> running = records.stream()
                .filter(record -> InstanceStatus.RUNNING.name().equals(record.get("status"))).toList();
        instance.setConditionBlocked(running.stream().anyMatch(record -> Boolean.TRUE.equals(record.get("conditionBlocked"))));
        Set<String> activeSteps = running.stream().map(record -> Objects.toString(record.get("currentStepId"), ""))
                .filter(value -> !value.isBlank()).collect(java.util.stream.Collectors.toSet());
        instance.setCurrentStep(activeSteps.size() == 1
                ? steps.findById(UUID.fromString(activeSteps.iterator().next())).orElse(null) : null);
        if (running.isEmpty()) {
            instance.setStatus(InstanceStatus.COMPLETED);
            instance.setCompletedAt(LocalDateTime.now());
            notificationCenter.create(instance.getCreatedBy(), "Batch đã hoàn tất",
                    instance.getRequestCode() + " đã xử lý xong " + records.size() + " hồ sơ.",
                    instance.getRequestCode(), "/instances/" + instance.getId());
        }
    }

    private void notifyBatchRecordAtEnd(WorkflowInstance instance, Map<String, Object> fields,
            Map<String, Object> config) {
        recordRecipient(instance, fields).ifPresent(user -> notificationCenter.create(user,
                codec.render(Objects.toString(config.get("title"), "Workflow đã kết thúc"), instance, fields),
                codec.render(Objects.toString(config.get("message"), instance.getRequestCode()), instance, fields),
                instance.getRequestCode(), "/instances/" + instance.getId()));
    }

    private void withRecordSnapshot(WorkflowInstance instance, Map<String, Object> fields, Runnable action) {
        String original = instance.getFieldSnapshot();
        instance.setFieldSnapshot(codec.write(fields));
        try { action.run(); }
        finally { instance.setFieldSnapshot(original); }
    }

    private WorkflowConnection select(List<WorkflowConnection> outgoing, ConnectionType desired,
            Map<String, Object> snapshot) {
        if (desired != null)
            return outgoing.stream().filter(c -> c.getType() == desired).findFirst().orElse(null);
        List<WorkflowConnection> conditions = outgoing.stream().filter(c -> c.getType() == ConnectionType.IF)
                .toList();
        if (!conditions.isEmpty()) {
            Optional<WorkflowConnection> matched = conditions.stream()
                    .filter(condition -> evaluator.matches(condition, snapshot)).findFirst();
            if (matched.isPresent())
                return matched.get();
            return outgoing.stream().filter(c -> c.getType() == ConnectionType.ELSE).findFirst().orElse(null);
        }
        return outgoing.stream().filter(c -> c.getType() == ConnectionType.DEFAULT).findFirst()
                .orElseGet(() -> outgoing.size() == 1 ? outgoing.get(0) : null);
    }

    private void createTasks(WorkflowInstance instance, WorkflowStep step) {
        boolean alreadyCreated = tasks.findByInstanceIdAndStatus(instance.getId(), TaskStatus.PENDING).stream()
                .anyMatch(task -> task.getStep().getId().equals(step.getId()));
        if (alreadyCreated)
            return;
        Map<String, Object> config = codec.stepConfig(step);
        List<User> assignees = actorResolver.resolve(instance, config);
        if (assignees.isEmpty())
            throw new IllegalStateException("Step '" + step.getLabel() + "' cannot resolve an active approver");
        Integer hours = config.get("deadlineHours") instanceof Number n ? n.intValue() : null;
        LocalDateTime deadline = null;
        String deadlineDate = Objects.toString(config.get("deadlineDate"), "");
        if (!deadlineDate.isBlank())
            deadline = LocalDate.parse(deadlineDate).atTime(23, 59, 59);
        else if (hours != null)
            deadline = LocalDateTime.now().plusHours(hours);
        UUID activationId = UUID.randomUUID();
        for (User assignee : assignees) {
            WorkflowTask task = tasks.save(WorkflowTask.builder().instance(instance).step(step).assignee(assignee)
                    .activationId(activationId).deadlineAt(deadline).build());
            notificationCenter.create(assignee, "Bạn có task mới", instance.getRequestCode() + " - " + step.getLabel(),
                    instance.getRequestCode(), "/tasks/" + task.getId());
        }
    }

    private boolean completionThresholdReached(UUID instanceId, WorkflowStep step, WorkflowTask completedTask,
            Map<String, Object> config) {
        String mode = Objects.toString(config.get("completionMode"), "ANY");
        if ("ANY".equals(mode))
            return true;
        List<WorkflowTask> batch = completedTask.getActivationId() == null
                ? tasks.findByInstanceIdAndStatus(instanceId, TaskStatus.PENDING).stream()
                        .filter(task -> task.getStep().getId().equals(step.getId())).toList()
                : tasks.findByInstanceIdAndStepIdAndActivationId(instanceId, step.getId(), completedTask.getActivationId());
        long completed = batch.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
        int total = batch.size();
        if (completedTask.getActivationId() == null) {
            completed++;
            total++;
        }
        if ("ALL".equals(mode))
            return completed >= total;
        int percentage = config.get("completionPercentage") instanceof Number number ? number.intValue() : 50;
        int required = Math.max(1, (int) Math.ceil(total * Math.max(1, Math.min(100, percentage)) / 100.0));
        return completed >= required;
    }

    private void cancelRemainingTasks(UUID instanceId, UUID stepId) {
        tasks.findByInstanceIdAndStatus(instanceId, TaskStatus.PENDING).stream()
                .filter(task -> task.getStep().getId().equals(stepId))
                .forEach(task -> {
                    task.setStatus(TaskStatus.CANCELLED);
                    task.setCompletedAt(LocalDateTime.now());
                    tasks.save(task);
                });
    }

    private String notificationTrigger(WorkflowStep source, ConnectionType desired) {
        if (desired == ConnectionType.APPROVE)
            return "APPROVED";
        if (desired == ConnectionType.REJECT)
            return "REJECTED";
        if (desired == ConnectionType.REVIEW_PASS)
            return "COMPLETED";
        if (desired == ConnectionType.REVIEW_FAIL)
            return "REJECTED";
        if (source.getType() == StepType.REVIEW || source.getType() == StepType.ASSIGNMENT)
            return "COMPLETED";
        return "STEP_ACTIVATED";
    }

    private void customNotification(WorkflowInstance instance, WorkflowStep step, String trigger) {
        Map<String, Object> config = codec.stepConfig(step);
        String title = codec.template(Objects.toString(config.get("titleTemplate"), "Thông báo workflow"), instance);
        String body = codec.template(Objects.toString(config.get("bodyTemplate"), instance.getRequestCode()), instance);
        Set<String> triggers = new HashSet<>();
        for (Object value : (Collection<?>) config.getOrDefault("triggers", List.of("STEP_ACTIVATED")))
            triggers.add(value.toString());
        if (!triggers.contains(trigger))
            return;
        Set<UUID> recipients = new HashSet<>();
        if (Boolean.TRUE.equals(config.get("includeRequester")))
            recipients.add(instance.getCreatedBy().getId());
        for (Object id : (List<?>) config.getOrDefault("recipientUserIds", List.of()))
            recipients.add(UUID.fromString(id.toString()));
        for (Object id : (List<?>) config.getOrDefault("recipientGroupIds", List.of()))
            groupRepository.findById(UUID.fromString(id.toString()))
                    .ifPresent(g -> g.getMembers().forEach(u -> recipients.add(u.getId())));
        for (Object role : (Collection<?>) config.getOrDefault("recipientRoles", List.of()))
            try {
                users.findDistinctBySystemRolesContainingAndActiveTrue(SystemRole.valueOf(role.toString()))
                        .forEach(u -> recipients.add(u.getId()));
            } catch (IllegalArgumentException ignored) {
            }
        // The transition is executed before the action audit row is appended, so
        // the authenticated runtime actor is the reliable previous-step actor.
        if (Boolean.TRUE.equals(config.get("includePreviousActor")))
            recipients.add(previousRuntimeActor(instance).getId());
        if (Boolean.TRUE.equals(config.get("includeRecordRecipient")))
            recordRecipient(instance).ifPresent(user -> recipients.add(user.getId()));
        if (Boolean.TRUE.equals(config.get("includeNextStepActors"))) {
            WorkflowConnection nextConnection = select(connections.findByFromStepId(step.getId()), null,
                    codec.snapshot(instance.getFieldSnapshot()));
            if (nextConnection != null)
                actorResolver.resolve(instance, codec.stepConfig(nextConnection.getToStep()))
                        .forEach(user -> recipients.add(user.getId()));
        }
        List<User> resolvedRecipients = recipients.stream().map(users::findById).flatMap(Optional::stream)
                .filter(User::isActive).toList();
        Set<String> channels = new HashSet<>();
        for (Object value : (Collection<?>) config.getOrDefault("channels", List.of("IN_APP")))
            channels.add(value.toString());
        if (channels.contains("IN_APP"))
            resolvedRecipients.forEach(u -> notificationCenter.create(u, title, body, instance.getRequestCode(),
                    "/instances/" + instance.getId()));
        notificationStepDelivery.deliver(channels, resolvedRecipients, title, body,
                Objects.toString(config.get("webhookUrl"), ""), instance, step);
    }

    private User previousRuntimeActor(WorkflowInstance instance) {
        try {
            User authenticated = currentUser.user();
            if (authenticated != null) return authenticated;
        }
        catch (RuntimeException ignored) {
        }
        List<InstanceStepLog> history = logs.findByInstanceIdOrderByActedAtAsc(instance.getId());
        for (int index = history.size() - 1; index >= 0; index--)
            if (history.get(index).getActor() != null) return history.get(index).getActor();
        return instance.getCreatedBy();
    }

    private boolean autoMatches(Map<String, Object> config, Map<String, Object> snapshot) {
        List<Map<String, Object>> clauses = (List<Map<String, Object>>) config.getOrDefault("autoConditions",
                List.of());
        boolean and = !"OR".equals(config.get("logicalOperator"));
        boolean result = and;
        for (Map<String, Object> c : clauses) {
            boolean one = rawEvaluate(Objects.toString(c.get("operator")), snapshot.get(c.get("fieldKey")),
                    c.get("expectedValue"));
            result = and ? result && one : result || one;
        }
        return result;
    }

    private boolean rawEvaluate(String op, Object actual, Object expected) {
        try {
            WorkflowConditionClause clause = WorkflowConditionClause.builder()
                    .operator(ConditionOperator.valueOf(op.toUpperCase(Locale.ROOT)))
                    .expectedValue(expected == null ? null : String.valueOf(expected)).build();
            return evaluator.evaluate(clause, actual);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private void validateTaskAction(WorkflowStep step, String action) {
        boolean valid = switch (step.getType()) {
            case APPROVAL -> Set.of("APPROVE", "REJECT").contains(action);
            case REVIEW -> Set.of("COMPLETE", "REJECT").contains(action);
            case ASSIGNMENT -> Set.of("COMPLETE", "FAIL").contains(action);
            default -> false;
        };
        if (!valid)
            throw new IllegalArgumentException("Action '" + action + "' is not valid for " + step.getType() + " Step");
        if (step.getType() == StepType.REVIEW && "REJECT".equals(action)
                && "COMMENT_ONLY".equals(codec.stepConfig(step).get("resultMode")))
            throw new IllegalArgumentException("Review COMMENT_ONLY does not support a blocking result");
    }

    private void requireReEvaluateAccess(WorkflowInstance instance) {
        if (currentUser.hasRole(SystemRole.ADMIN) || instance.getWorkflow().getOwner().getId().equals(currentUser.id()))
            return;
        boolean currentActor = tasks.findByInstanceIdAndStatus(instance.getId(), TaskStatus.PENDING).stream()
                .anyMatch(task -> task.getAssignee().getId().equals(currentUser.id())
                        && (instance.getCurrentStep() == null
                                || task.getStep().getId().equals(instance.getCurrentStep().getId())));
        if (!currentActor)
            throw new AccessDeniedException(
                    "Only admin, workflow owner or current actor can re-evaluate the condition");
    }

    private void requireRuntimeAccess(WorkflowInstance i) {
        if (currentUser.hasRole(SystemRole.ADMIN) || i.getCreatedBy().getId().equals(currentUser.id())
                || i.getWorkflow().getOwner().getId().equals(currentUser.id()))
            return;
        if (tasks.existsByInstanceIdAndAssigneeId(i.getId(), currentUser.id()))
            return;
        if (canViewAsRecordRecipient(i, currentUser.id()))
            return;
        throw new AccessDeniedException("Cannot view this request");
    }

    private Optional<User> recordRecipient(WorkflowInstance instance) {
        return recordRecipient(instance, codec.snapshot(instance.getFieldSnapshot()));
    }

    private Optional<User> recordRecipient(WorkflowInstance instance, Map<String, Object> fields) {
        WorkflowStep start = startStep(instance.getWorkflow());
        String fieldKey = Objects.toString(startConfig(instance.getWorkflow(),start).get("recordRecipientFieldKey"), "").trim();
        if (fieldKey.isBlank())
            return Optional.empty();
        Object raw = fields.get(fieldKey);
        String value = Objects.toString(raw, "").trim();
        if (value.isBlank())
            return Optional.empty();
        try {
            Optional<User> byId = users.findById(UUID.fromString(value)).filter(User::isActive);
            if (byId.isPresent()) return byId;
        } catch (IllegalArgumentException ignored) {
        }
        return users.findByEmailIgnoreCase(value).filter(User::isActive);
    }

    private boolean isBatch(WorkflowInstance instance) {
        return instance.getBatchId() != null || Boolean.TRUE.equals(codec.snapshot(instance.getFieldSnapshot()).get("_batch"));
    }

    @SuppressWarnings("unchecked")
    private boolean canViewAsRecordRecipient(WorkflowInstance instance, UUID userId) {
        if (!isBatch(instance))
            return recordRecipient(instance).map(user -> user.getId().equals(userId)).orElse(false);
        return batchRecords(instance).stream().anyMatch(record -> {
            Object rawFields = record.get("fields");
            return rawFields instanceof Map<?, ?> values
                    && recordRecipient(instance, (Map<String, Object>) values)
                            .map(user -> user.getId().equals(userId)).orElse(false);
        });
    }

    private boolean hasFullBatchAccess(WorkflowInstance instance) {
        return currentUser.hasRole(SystemRole.ADMIN)
                || instance.getCreatedBy().getId().equals(currentUser.id())
                || instance.getWorkflow().getOwner().getId().equals(currentUser.id());
    }

    private List<WorkflowBatchRecord> latestBatchRecordEntities(UUID instanceId) {
        Map<Integer, WorkflowBatchRecord> latest = new LinkedHashMap<>();
        for (WorkflowBatchRecord row : normalizedBatchRecords.findByInstanceIdOrderByRowNumberAscRevisionDesc(instanceId))
            latest.putIfAbsent(row.getRowNumber(), row);
        return new ArrayList<>(latest.values());
    }

    private boolean canViewBatchRecord(WorkflowInstance instance, WorkflowBatchRecord row) {
        if (hasFullBatchAccess(instance)) return true;
        if (row.getId() != null && batchRecordAccess.existsByBatchRecordIdAndUserId(row.getId(), currentUser.id()))
            return true;
        return canViewBatchState(instance, codec.snapshot(row.getStateJson()));
    }

    @SuppressWarnings("unchecked")
    private boolean canViewBatchState(WorkflowInstance instance, Map<String, Object> state) {
        if (hasFullBatchAccess(instance)) return true;
        if (state.get("actorIds") instanceof Collection<?> actorIds
                && actorIds.stream().anyMatch(value -> currentUser.id().toString().equals(value.toString()))) return true;
        Object rawFields = state.get("fields");
        return rawFields instanceof Map<?, ?> values
                && recordRecipient(instance, (Map<String, Object>) values)
                        .map(user -> user.getId().equals(currentUser.id())).orElse(false);
    }

    private Map<String, Object> batchRecordSummary(WorkflowBatchRecord row) {
        Map<String, Object> state = codec.snapshot(row.getStateJson());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowNumber", row.getRowNumber());
        result.put("businessKey", row.getBusinessKey());
        result.put("revision", row.getRevision());
        result.put("status", row.getStatus());
        result.put("currentStepId", row.getCurrentStep() == null ? state.get("currentStepId") : row.getCurrentStep().getId());
        result.put("currentStepLabel", row.getCurrentStep() == null ? state.get("currentStepLabel") : row.getCurrentStep().getLabel());
        result.put("currentStepType", state.get("currentStepType"));
        result.put("lastOutcome", row.getLastOutcome() == null ? state.get("lastOutcome") : row.getLastOutcome());
        result.put("lastReason", row.getLastReason() == null ? state.get("lastReason") : row.getLastReason());
        result.put("lastActorId", row.getLastActor() == null ? state.get("lastActorId") : row.getLastActor().getId());
        result.put("lastActorName", row.getLastActor() == null ? null : row.getLastActor().getDisplayName());
        result.put("lastActionAt", row.getLastActionAt() == null ? state.get("lastActionAt") : row.getLastActionAt());
        result.put("completedAt", row.getCompletedAt() == null ? state.get("completedAt") : row.getCompletedAt());
        return result;
    }

    private Map<String, Object> legacyBatchSummary(Map<String, Object> state) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : List.of("rowNumber", "businessKey", "revision", "status", "currentStepId",
                "currentStepLabel", "currentStepType", "lastOutcome", "lastReason", "lastActorId",
                "lastActorName", "lastActionAt", "completedAt"))
            result.put(key, state.get(key));
        return result;
    }

    private List<Map<String, Object>> batchSystemActions(WorkflowInstance instance, int rowNumber) {
        boolean privileged = currentUser.hasRole(SystemRole.ADMIN)
                || instance.getWorkflow().getOwner().getId().equals(currentUser.id());
        return systemActionExecutions.findByInstanceIdAndBatchRowNumberOrderByCreatedAtAsc(instance.getId(), rowNumber)
                .stream().map(execution -> {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", execution.getId());
                    value.put("stepLabel", execution.getStep().getLabel());
                    value.put("status", execution.getStatus());
                    value.put("attemptCount", execution.getAttemptCount());
                    value.put("maxAttempts", execution.getMaxAttempts());
                    value.put("responseStatus", execution.getResponseStatus());
                    value.put("startedAt", execution.getStartedAt());
                    value.put("completedAt", execution.getCompletedAt());
                    value.put("errorMessage", execution.getErrorMessage() == null ? null : privileged
                            ? execution.getErrorMessage() : "System Action thất bại (execution " + execution.getId() + ")");
                    if (privileged) {
                        value.put("httpMethod", execution.getHttpMethod());
                        value.put("requestUrl", execution.getRequestUrl());
                        value.put("responseBody", execution.getResponseBody());
                        value.put("mappedOutputs", codec.snapshot(execution.getMappedOutputsJson()));
                    }
                    return value;
                }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> batchRecords(WorkflowInstance instance) {
        List<WorkflowBatchRecord> normalized = normalizedBatchRecords.findByInstanceIdOrderByRowNumberAscRevisionDesc(instance.getId());
        if (!normalized.isEmpty()) {
            Map<Integer, Map<String, Object>> latest = new LinkedHashMap<>();
            for (WorkflowBatchRecord row : normalized)
                latest.putIfAbsent(row.getRowNumber(), codec.snapshot(row.getStateJson()));
            return new ArrayList<>(latest.values());
        }
        Object value = codec.snapshot(instance.getFieldSnapshot()).get("records");
        return value instanceof List<?> list ? (List<Map<String, Object>>) list : new ArrayList<>();
    }

    private void requireRunning(WorkflowInstance i) {
        if (i.getStatus() != InstanceStatus.RUNNING)
            throw new IllegalStateException("Request is already terminal");
    }

    private WorkflowInstance instance(UUID id) {
        return instances.findById(id).orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", "id", id));
    }

    private Workflow activeWorkflow(UUID workflowId) {
        Workflow requested = workflows.findById(workflowId)
                .orElseThrow(() -> new ResourceNotFoundException("Workflow", "id", workflowId));
        if (requested.getStatus() == WorkflowStatus.PUBLISHED)
            return requested;
        return workflows.findByFamilyIdAndStatus(requested.getFamilyId(), WorkflowStatus.PUBLISHED).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow này hiện không có phiên bản đang chạy"));
    }

    private WorkflowStep startStep(Workflow workflow) {
        return steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId()).stream()
                .filter(step -> step.getType() == StepType.START).findFirst()
                .orElseThrow(() -> new IllegalStateException("Workflow has no START step"));
    }

    private FormVersion requireForm(Workflow workflow) {
        if (workflow.getFormVersion() == null)
            throw new IllegalStateException("Workflow has no published form attached");
        return workflow.getFormVersion();
    }

    private Map<String,Object> startConfig(Workflow workflow,WorkflowStep start){
        Map<String,Object> config=new LinkedHashMap<>(codec.stepConfig(start));
        FormVersion form=requireForm(workflow);config.put("instructionForCreator",form.getInstruction());
        config.put("submissionMode",form.getSubmissionMode());config.put("recordRecipientFieldKey",form.getRecordRecipientFieldKey());
        config.put("maxBatchRows",form.getMaxBatchRows());return config;
    }

    private void requireSubmitAccess(Workflow workflow) {
        if (!workflowAuthorization.canSubmit(workflow))
            throw new AccessDeniedException("Current user is outside the workflow audience");
        if (!currentUser.user().isActive())
            throw new AccessDeniedException("Inactive users cannot submit forms");
    }

    private Map<String, Object> draftResponse(RequestDraft draft, Workflow activeWorkflow) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", draft.getId());
        result.put("workflowId", draft.getWorkflowVersion().getId());
        result.put("workflowVersion", draft.getWorkflowVersion().getVersion());
        result.put("versionChanged", !draft.getWorkflowVersion().getId().equals(activeWorkflow.getId()));
        result.put("fields", codec.snapshot(draft.getFieldSnapshot()));
        result.put("updatedAt", draft.getUpdatedAt());
        return result;
    }

    private BigDecimal decimal(String v) {
        try {
            return new BigDecimal(v);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid number: " + v);
        }
    }

    private String code() {
        return "REQ-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private void log(WorkflowInstance i, WorkflowStep s, User actor, String action, String comment) {
        logs.save(InstanceStepLog.builder().instance(i).step(s).actor(actor).action(action).comment(comment).build());
    }

    private InstanceResponse response(WorkflowInstance i) {
        boolean batch = isBatch(i);
        List<Map<String, Object>> records = batch ? batchRecords(i) : List.of();
        Map<String, Long> counts = batch ? records.stream().collect(java.util.stream.Collectors.groupingBy(
                record -> Objects.toString(record.get("status"), InstanceStatus.RUNNING.name()),
                LinkedHashMap::new, java.util.stream.Collectors.counting())) : Map.of();
        return InstanceResponse.builder().id(i.getId()).requestCode(i.getRequestCode())
                .batchId(i.getBatchId()).batchRowNumber(i.getBatchRowNumber())
                .batch(batch).batchTotal(batch ? records.size() : null).batchStatusCounts(counts)
                .workflowId(i.getWorkflow().getId()).workflowName(i.getWorkflow().getName())
                .workflowVersion(i.getWorkflow().getVersion())
                .formId(i.getFormVersion()==null?null:i.getFormVersion().getForm().getId()).formVersionId(i.getFormVersion()==null?null:i.getFormVersion().getId())
                .formVersionNumber(i.getFormVersion()==null?null:i.getFormVersion().getVersionNumber()).formName(i.getFormVersion()==null?null:i.getFormVersion().getForm().getName())
                .createdById(i.getCreatedBy().getId())
                .createdByName(i.getCreatedBy().getDisplayName())
                .currentStepId(i.getCurrentStep() == null ? null : i.getCurrentStep().getId())
                .currentStepLabel(i.getCurrentStep() == null ? (batch && i.getStatus() == InstanceStatus.RUNNING
                        ? "Nhiều bước đang xử lý" : null) : i.getCurrentStep().getLabel())
                .currentStepType(i.getCurrentStep() == null ? (batch ? "BATCH" : null) : i.getCurrentStep().getType().name())
                .status(i.getStatus()).conditionBlocked(i.isConditionBlocked())
                .fields(batch ? Map.of("batchTotal", records.size(), "statusCounts", counts)
                        : codec.snapshot(i.getFieldSnapshot()))
                .startedAt(i.getStartedAt()).completedAt(i.getCompletedAt())
                .withdrawnById(i.getWithdrawnBy() == null ? null : i.getWithdrawnBy().getId())
                .withdrawnByName(i.getWithdrawnBy() == null ? null : i.getWithdrawnBy().getDisplayName())
                .withdrawnAt(i.getWithdrawnAt()).withdrawalReason(i.getWithdrawalReason())
                .requesterWithdrawalAllowed(i.isRequesterWithdrawalAllowed()).build();
    }

    private InstanceResponse instanceSummaryResponse(WorkflowInstance instance) {
        boolean batch = isBatch(instance);
        return InstanceResponse.builder().id(instance.getId()).requestCode(instance.getRequestCode())
                .batchId(instance.getBatchId()).batchRowNumber(instance.getBatchRowNumber()).batch(batch)
                .workflowId(instance.getWorkflow().getId()).workflowName(instance.getWorkflow().getName())
                .workflowVersion(instance.getWorkflow().getVersion())
                .formId(instance.getFormVersion()==null?null:instance.getFormVersion().getForm().getId()).formVersionId(instance.getFormVersion()==null?null:instance.getFormVersion().getId())
                .formVersionNumber(instance.getFormVersion()==null?null:instance.getFormVersion().getVersionNumber()).formName(instance.getFormVersion()==null?null:instance.getFormVersion().getForm().getName())
                .createdById(instance.getCreatedBy().getId())
                .createdByName(instance.getCreatedBy().getDisplayName())
                .currentStepId(instance.getCurrentStep() == null ? null : instance.getCurrentStep().getId())
                .currentStepLabel(instance.getCurrentStep() == null
                        ? (batch && instance.getStatus() == InstanceStatus.RUNNING ? "Nhiều bước đang xử lý" : null)
                        : instance.getCurrentStep().getLabel())
                .currentStepType(instance.getCurrentStep() == null ? (batch ? "BATCH" : null)
                        : instance.getCurrentStep().getType().name())
                .status(instance.getStatus()).conditionBlocked(instance.isConditionBlocked())
                .startedAt(instance.getStartedAt()).completedAt(instance.getCompletedAt())
                .withdrawnAt(instance.getWithdrawnAt()).withdrawalReason(instance.getWithdrawalReason())
                .requesterWithdrawalAllowed(instance.isRequesterWithdrawalAllowed()).build();
    }

    private List<com.company.workflowbuilder.dto.CalculatedOutput> taskOutputDefinitions(WorkflowTask task, TaskActionRequest request) {
        var outputs = request.getCalculatedOutputs() == null
                ? codec.calculatedOutputs(codec.stepConfig(task.getStep()).get("calculatedOutputs")) : request.getCalculatedOutputs();
        if (!outputs.isEmpty() && task.getStep().getType() != StepType.REVIEW)
            throw new IllegalArgumentException("Chỉ Reviewer được bổ sung output tính toán");
        return outputs;
    }

    private List<Map<String, Object>> calculateTaskOutputs(WorkflowTask task, TaskActionRequest request, List<Map<String, Object>> rows) {
        var outputs = taskOutputDefinitions(task, request);
        if (outputs.isEmpty()) return List.of();
        var results = com.company.workflowbuilder.service.runtime.OutputCalculator.calculate(outputs, calculationInput(task, outputs, rows));
        task.setCalculatedResults(codec.write(Map.of("outputs", outputs, "rows", results)));
        return results;
    }

    private List<Map<String, Object>> calculationInput(WorkflowTask task,
            List<com.company.workflowbuilder.dto.CalculatedOutput> outputs, List<Map<String, Object>> rows) {
        Set<String> previousOutputs = new HashSet<>();
        tasks.findByInstanceIdAndStepIdAndActivationId(task.getInstance().getId(), task.getStep().getId(), task.getActivationId())
                .stream().filter(previous -> previous.getStatus() == TaskStatus.COMPLETED && previous.getCalculatedResults() != null)
                .forEach(previous -> codec.calculatedOutputs(codec.snapshot(previous.getCalculatedResults()).get("outputs"))
                        .forEach(output -> previousOutputs.add(output.fieldKey())));
        previousOutputs.retainAll(outputs.stream().map(com.company.workflowbuilder.dto.CalculatedOutput::fieldKey).toList());
        return rows.stream().map(row -> {
            Map<String, Object> copy = new LinkedHashMap<>(row);
            previousOutputs.forEach(copy::remove);
            return copy;
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> previewTaskOutputs(UUID taskId, TaskActionRequest request) {
        WorkflowTask task = tasks.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowTask", "id", taskId));
        if (task.getStatus() != TaskStatus.PENDING || !task.getAssignee().getId().equals(currentUser.id()))
            throw new AccessDeniedException("Task không được giao cho user hiện tại");
        requireRunning(task.getInstance());
        if (task.getStep().getType() != StepType.REVIEW)
            throw new IllegalArgumentException("Chỉ Reviewer được tính thử output");
        List<Map<String, Object>> rows;
        if (isBatch(task.getInstance())) {
            rows = batchRecords(task.getInstance()).stream()
                    .filter(record -> task.getStep().getId().toString().equals(record.get("currentStepId")))
                    .filter(record -> task.getActivationId().toString().equals(record.get("activationId")))
                    .map(record -> codec.snapshot(codec.write(record.get("fields")))).toList();
        } else {
            var row = codec.snapshot(task.getInstance().getFieldSnapshot());
            if (request.getFields() != null) {
                for (var field : fieldValidation.definitions(task.getStep().getId())) {
                    if (request.getFields().containsKey(field.getFieldKey())) row.put(field.getFieldKey(), request.getFields().get(field.getFieldKey()));
                }
            }
            rows = List.of(row);
        }
        var outputs = taskOutputDefinitions(task, request);
        return com.company.workflowbuilder.service.runtime.OutputCalculator.calculate(outputs, calculationInput(task, outputs, rows));
    }

    private String prepareReviewResults(WorkflowTask task, TaskActionRequest request) {
        var items = request.getReviewResults();
        if (items == null || items.isEmpty()) return request.getComment();
        if (task.getStep().getType() != StepType.REVIEW)
            throw new IllegalArgumentException("Chỉ Reviewer được bổ sung kết quả review");
        if (items.size() > 50)
            throw new IllegalArgumentException("Tối đa 50 mục kết quả bổ sung");
        var normalized = new ArrayList<com.company.workflowbuilder.dto.ReviewResultItem>();
        for (var item : items) {
            if (item == null || item.label() == null || item.label().isBlank()
                    || item.content() == null || item.content().isBlank())
                throw new IllegalArgumentException("Mỗi mục kết quả phải có tên và nội dung");
            if (item.label().length() > 200 || item.content().length() > 5000)
                throw new IllegalArgumentException("Tên mục tối đa 200 ký tự, nội dung tối đa 5000 ký tự");
            normalized.add(new com.company.workflowbuilder.dto.ReviewResultItem(item.label().trim(), item.content().trim()));
        }
        task.setReviewResults(codec.write(normalized));
        return Objects.toString(request.getComment(), "") + "\n\nKết quả bổ sung của Reviewer:\n"
                + normalized.stream().map(item -> item.label() + ": " + item.content())
                        .collect(java.util.stream.Collectors.joining("\n"));
    }

    private void storeReviewHandoff(WorkflowTask task, String action, TaskActionRequest request,
            List<Map<String, Object>> reviewedRecords) {
        if (!Boolean.TRUE.equals(codec.stepConfig(task.getStep()).get("forwardReviewHandoff"))) return;
        User reviewer = currentUser.user();
        Map<String, Object> handoff = new LinkedHashMap<>();
        handoff.put("sourceTaskId", task.getId());
        handoff.put("sourceStepId", task.getStep().getId());
        handoff.put("sourceStepLabel", task.getStep().getLabel());
        handoff.put("reviewerId", reviewer.getId());
        handoff.put("reviewerName", reviewer.getDisplayName());
        handoff.put("action", action);
        handoff.put("comment", Objects.toString(request.getComment(), "").trim());
        handoff.put("additionalResults", codec.reviewResults(task.getReviewResults()));
        handoff.put("reviewedAt", LocalDateTime.now().toString());
        handoff.put("batch", isBatch(task.getInstance()));
        handoff.put("records", reviewedRecords);
        task.setReviewHandoff(codec.write(handoff));
    }

    private List<Map<String, Object>> reviewHandoffsFor(WorkflowTask target) {
        LocalDateTime targetCreatedAt = target.getCreatedAt();
        return tasks.findByInstanceIdAndStatusOrderByCompletedAtAsc(target.getInstance().getId(), TaskStatus.COMPLETED)
                .stream()
                .filter(source -> !source.getId().equals(target.getId()))
                .filter(source -> source.getReviewHandoff() != null && !source.getReviewHandoff().isBlank())
                .filter(source -> targetCreatedAt == null || source.getCompletedAt() == null
                        || !source.getCompletedAt().isAfter(targetCreatedAt))
                .map(source -> codec.snapshot(source.getReviewHandoff()))
                .toList();
    }

    private TaskResponse taskResponse(WorkflowTask t) {
        Map<String, Object> stepConfig = codec.stepConfig(t.getStep());
        Map<String, Object> calculation = t.getCalculatedResults() == null ? Map.of() : codec.snapshot(t.getCalculatedResults());
        boolean batch = isBatch(t.getInstance());
        List<Map<String, Object>> taskRecords = batch ? batchRecords(t.getInstance()).stream()
                .filter(record -> t.getStep().getId().toString().equals(record.get("currentStepId")))
                .filter(record -> t.getActivationId().toString().equals(record.get("activationId")))
                .map(record -> Map.<String, Object>of(
                        "rowNumber", record.get("rowNumber"),
                        "fields", record.getOrDefault("fields", Map.of())))
                .toList() : List.of();
        return TaskResponse.builder().id(t.getId()).instanceId(t.getInstance().getId())
                .instanceStatus(t.getInstance().getStatus().name())
                .instanceCurrentStepId(t.getInstance().getCurrentStep() == null ? null : t.getInstance().getCurrentStep().getId())
                .requestCode(t.getInstance().getRequestCode()).workflowName(t.getInstance().getWorkflow().getName())
                .requesterName(t.getInstance().getCreatedBy().getDisplayName()).stepId(t.getStep().getId())
                .stepLabel(t.getStep().getLabel()).stepType(t.getStep().getType().name()).status(t.getStatus().name())
                .fields(batch ? Map.of() : codec.snapshot(t.getInstance().getFieldSnapshot()))
                .reviewResults(codec.reviewResults(t.getReviewResults()))
                .reviewHandoffs(reviewHandoffsFor(t))
                .calculatedOutputs(codec.calculatedOutputs(t.getStatus() == TaskStatus.PENDING
                        ? stepConfig.get("calculatedOutputs") : calculation.get("outputs")))
                .calculatedRows(calculation.get("rows") instanceof List<?> rows ? (List<Map<String, Object>>) rows : List.of())
                .fieldDefinitions(fieldValidation.definitions(t.getStep().getId()))
                .batch(batch).batchRecords(taskRecords)
                .commentRequired(Boolean.TRUE.equals(stepConfig.get("commentRequired")))
                .fieldsEditable(t.getStatus() == TaskStatus.PENDING &&
                        (t.getStep().getType() == StepType.REVIEW || t.getStep().getType() == StepType.APPROVAL
                                || t.getStep().getType() == StepType.ASSIGNMENT))
                .resultMode(Objects.toString(stepConfig.get("resultMode"), ""))
                .canFail(t.getStep().getType() == StepType.ASSIGNMENT
                        && connections.findByFromStepId(t.getStep().getId()).stream()
                                .anyMatch(connection -> connection.getType() == ConnectionType.ASSIGNMENT_FAIL))
                .deadlineAt(t.getDeadlineAt())
                .createdAt(t.getCreatedAt()).completedAt(t.getCompletedAt()).build();
    }

    private TaskResponse taskSummaryResponse(WorkflowTask task) {
        return TaskResponse.builder().id(task.getId()).instanceId(task.getInstance().getId())
                .instanceStatus(task.getInstance().getStatus().name())
                .instanceCurrentStepId(task.getInstance().getCurrentStep() == null ? null : task.getInstance().getCurrentStep().getId())
                .requestCode(task.getInstance().getRequestCode())
                .workflowName(task.getInstance().getWorkflow().getName())
                .requesterName(task.getInstance().getCreatedBy().getDisplayName())
                .stepId(task.getStep().getId()).stepLabel(task.getStep().getLabel())
                .stepType(task.getStep().getType().name()).status(task.getStatus().name())
                .deadlineAt(task.getDeadlineAt()).createdAt(task.getCreatedAt())
                .completedAt(task.getCompletedAt()).build();
    }
}
