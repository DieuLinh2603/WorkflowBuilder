package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.CreateInstanceRequest;
import com.company.workflowbuilder.dto.request.CreateBatchInstanceRequest;
import com.company.workflowbuilder.dto.request.TaskActionRequest;
import com.company.workflowbuilder.dto.response.BatchInstanceResponse;
import com.company.workflowbuilder.dto.response.InstanceResponse;
import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.runtime.WorkflowActorResolver;
import com.company.workflowbuilder.service.runtime.WorkflowFieldValidationService;
import com.company.workflowbuilder.service.runtime.WorkflowJsonCodec;
import com.company.workflowbuilder.service.runtime.WorkflowSystemActionExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkflowEngineServiceTest {
    // Runtime workflow tests share the production package model and repositories.
    @Mock WorkflowRepository workflows;
    @Mock WorkflowStepRepository steps;
    @Mock WorkflowConnectionRepository connections;
    @Mock CustomFieldDefinitionRepository fields;
    @Mock WorkflowInstanceRepository instances;
    @Mock WorkflowTaskRepository tasks;
    @Mock WorkflowBatchRecordRepository normalizedBatchRecords;
    @Mock DatasetVersionRepository datasetVersions;
    @Mock RequestDraftRepository drafts;
    @Mock InstanceStepLogRepository logs;
    @Mock UserRepository users;
    @Mock CurrentUserService currentUser;
    @Mock ConditionEvaluatorService evaluator;
    @Mock NotificationCenterService notificationCenter;
    @Mock NotificationStepDeliveryService notificationStepDelivery;
    @Mock WorkflowAuthorizationService authorization;
    @Mock UserGroupRepository groups;

    WorkflowEngineService engine;
    final ObjectMapper mapper = new ObjectMapper();
    final List<WorkflowTask> taskStore = new ArrayList<>();
    final List<InstanceStepLog> logStore = new ArrayList<>();
    final AtomicReference<User> authenticated = new AtomicReference<>();
    final AtomicReference<WorkflowInstance> instanceStore = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        WorkflowJsonCodec codec = new WorkflowJsonCodec(mapper);
        WorkflowFieldValidationService fieldValidation = new WorkflowFieldValidationService(fields);
        WorkflowActorResolver actorResolver = new WorkflowActorResolver(users, groups, currentUser);
        WorkflowSystemActionExecutor actionExecutor = new WorkflowSystemActionExecutor(instances, logs,
                notificationCenter, notificationStepDelivery, codec);
        engine = new WorkflowEngineService(workflows, steps, connections, instances, tasks, normalizedBatchRecords,
                datasetVersions, drafts, logs, users,
                currentUser, evaluator, notificationCenter, notificationStepDelivery, authorization, groups,
                codec, fieldValidation, actorResolver, actionExecutor);
        lenient().when(normalizedBatchRecords.findByInstanceIdOrderByRowNumberAscRevisionDesc(any())).thenReturn(List.of());
        lenient().when(normalizedBatchRecords.findFirstByInstanceIdAndRowNumberOrderByRevisionDesc(any(), anyInt())).thenReturn(Optional.empty());
        lenient().when(normalizedBatchRecords.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(currentUser.user()).thenAnswer(invocation -> authenticated.get());
        when(instances.save(any())).thenAnswer(invocation -> {
            WorkflowInstance value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            if (value.getStartedAt() == null) value.setStartedAt(LocalDateTime.now());
            instanceStore.set(value);
            return value;
        });
        lenient().when(instances.findById(any())).thenAnswer(invocation -> Optional.ofNullable(instanceStore.get())
                .filter(value -> value.getId().equals(invocation.getArgument(0))));
        lenient().when(tasks.save(any())).thenAnswer(invocation -> {
            WorkflowTask value = invocation.getArgument(0);
            if (value.getId() == null) { value.setId(UUID.randomUUID()); value.setCreatedAt(LocalDateTime.now()); taskStore.add(value); }
            return value;
        });
        lenient().when(tasks.findByInstanceIdAndStatus(any(), any())).thenAnswer(invocation -> taskStore.stream()
                .filter(task -> task.getInstance().getId().equals(invocation.getArgument(0)) && task.getStatus() == invocation.getArgument(1)).toList());
        lenient().when(tasks.findByInstanceIdAndStepIdAndActivationId(any(), any(), any())).thenAnswer(invocation -> taskStore.stream()
                .filter(task -> task.getInstance().getId().equals(invocation.getArgument(0))
                        && task.getStep().getId().equals(invocation.getArgument(1))
                        && Objects.equals(task.getActivationId(), invocation.getArgument(2))).toList());
        lenient().when(tasks.findById(any())).thenAnswer(invocation -> taskStore.stream()
                .filter(task -> task.getId().equals(invocation.getArgument(0))).findFirst());
        lenient().when(tasks.findByInstanceIdAndStepIdAndAssigneeIdAndStatus(any(), any(), any(), any())).thenAnswer(invocation -> taskStore.stream()
                .filter(task -> task.getInstance().getId().equals(invocation.getArgument(0))
                        && task.getStep().getId().equals(invocation.getArgument(1))
                        && task.getAssignee().getId().equals(invocation.getArgument(2))
                        && task.getStatus() == invocation.getArgument(3)).findFirst());
        when(logs.save(any())).thenAnswer(invocation -> { InstanceStepLog value=invocation.getArgument(0); if(value.getId()==null)value.setId(UUID.randomUUID()); logStore.add(value); return value; });
        lenient().when(fields.findByStepIdOrderByDisplayOrderAsc(any())).thenReturn(List.of());
    }

    @Test
    void runsManualAndAutomaticBusinessStepsUntilEnd() throws Exception {
        User requester = user("requester@company.com");
        User actor = user("actor@company.com");
        authenticated.set(requester);
        Workflow workflow = Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("End-to-end")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();

        WorkflowStep start = step(workflow, StepType.START, "Start", "{}");
        WorkflowStep approval = step(workflow, StepType.APPROVAL, "Approval", actorConfig(actor, Map.of("mode", "MANUAL")));
        WorkflowStep notification = step(workflow, StepType.NOTIFICATION, "Notification", mapper.writeValueAsString(Map.of(
                "titleTemplate", "Approved {{requestCode}}", "bodyTemplate", "{{workflowName}} approved",
                "channels", List.of("IN_APP"), "triggers", List.of("APPROVED"), "includeRequester", true)));
        WorkflowStep action = step(workflow, StepType.SYSTEM_ACTION, "Action", mapper.writeValueAsString(Map.of(
                "actionType", "UPDATE_DATA", "failurePolicy", "STOP",
                "mappings", List.of(Map.of("targetField", "processed", "valueTemplate", "yes")))));
        WorkflowStep review = step(workflow, StepType.REVIEW, "Review", actorConfig(actor, Map.of(
                "mode", "MANUAL", "commentRequired", true, "resultMode", "REQUIRE_APPROVAL")));
        WorkflowStep assignment = step(workflow, StepType.ASSIGNMENT, "Assignment", actorConfig(actor, Map.of("completionMode", "ANY")));
        WorkflowStep end = step(workflow, StepType.END, "End", mapper.writeValueAsString(Map.of("outcome", "COMPLETED", "notifyRequester", true)));

        Map<UUID,List<WorkflowConnection>> graph = new HashMap<>();
        graph.put(start.getId(), List.of(connection(workflow,start,approval,ConnectionType.DEFAULT)));
        graph.put(approval.getId(), List.of(connection(workflow,approval,notification,ConnectionType.APPROVE), connection(workflow,approval,end,ConnectionType.REJECT)));
        graph.put(notification.getId(), List.of(connection(workflow,notification,action,ConnectionType.DEFAULT)));
        graph.put(action.getId(), List.of(connection(workflow,action,review,ConnectionType.DEFAULT)));
        graph.put(review.getId(), List.of(connection(workflow,review,assignment,ConnectionType.DEFAULT)));
        graph.put(assignment.getId(), List.of(connection(workflow,assignment,end,ConnectionType.DEFAULT)));
        when(connections.findByFromStepId(any())).thenAnswer(invocation -> graph.getOrDefault(invocation.getArgument(0), List.of()));
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId())).thenReturn(List.of(start,approval,notification,action,review,assignment,end));
        when(authorization.canSubmit(workflow)).thenReturn(true);
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        when(users.findById(requester.getId())).thenReturn(Optional.of(requester));

        CreateInstanceRequest request = new CreateInstanceRequest();
        request.setWorkflowId(workflow.getId()); request.setFields(new HashMap<>());
        InstanceResponse submitted = engine.submit(request);
        assertThat(submitted.getCurrentStepType()).isEqualTo("APPROVAL");
        verify(notificationCenter).create(eq(requester),eq("Gửi yêu cầu thành công"),anyString(),anyString(),anyString());
        UUID instanceId = submitted.getId();

        authenticated.set(actor);
        TaskActionRequest actionRequest = new TaskActionRequest(); actionRequest.setComment("ok");
        InstanceResponse approved = engine.act(instanceId,"APPROVE",actionRequest);
        assertThat(approved.getCurrentStepType()).isEqualTo("REVIEW");
        assertThat(approved.getFields()).containsEntry("processed","yes");

        actionRequest.setReviewResults(List.of(new com.company.workflowbuilder.dto.ReviewResultItem("Kết luận", "Đã đối chiếu")));
        actionRequest.setCalculatedOutputs(List.of(new com.company.workflowbuilder.dto.CalculatedOutput("score", "Điểm", "(10 + 2) * 3")));
        InstanceResponse reviewed = engine.act(instanceId,"COMPLETE",actionRequest);
        assertThat(reviewed.getFields()).containsEntry("score", 36);
        assertThat(taskStore.stream().filter(task -> task.getStep().getType() == StepType.REVIEW).findFirst().orElseThrow().getReviewResults()).contains("Đã đối chiếu");
        assertThat(logStore).anyMatch(log -> log.getComment() != null && log.getComment().contains("Kết luận: Đã đối chiếu"));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> engine.act(instanceId,"COMPLETE",actionRequest));
        actionRequest.setReviewResults(List.of());
        actionRequest.setCalculatedOutputs(List.of());
        assertThat(reviewed.getCurrentStepType()).isEqualTo("ASSIGNMENT");
        InstanceResponse completed = engine.act(instanceId,"COMPLETE",actionRequest);
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(completed.getCurrentStepType()).isEqualTo("END");
        assertThat(taskStore).hasSize(3).allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
        verify(notificationCenter, atLeast(1)).create(eq(requester), contains("Approved"), anyString(), anyString(), anyString());
    }

    @Test
    void batchSubmissionCreatesOneParentInstanceWithIndependentRecordStates() throws Exception {
        User requester = user("batch-owner@company.com");
        authenticated.set(requester);
        Workflow workflow = Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("Batch flow")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();
        WorkflowStep start = step(workflow, StepType.START, "Start",
                mapper.writeValueAsString(Map.of("submissionMode", "BATCH", "maxBatchRows", 10)));
        WorkflowStep end = step(workflow, StepType.END, "End",
                mapper.writeValueAsString(Map.of("outcome", "COMPLETED", "notifyRequester", false)));
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId())).thenReturn(List.of(start, end));
        when(connections.findByFromStepId(start.getId())).thenReturn(List.of(
                connection(workflow, start, end, ConnectionType.DEFAULT)));
        when(authorization.canSubmit(workflow)).thenReturn(true);
        CreateBatchInstanceRequest request = new CreateBatchInstanceRequest();
        request.setWorkflowId(workflow.getId());
        request.setRecords(List.of(Map.of(), Map.of(), Map.of()));

        BatchInstanceResponse response = engine.submitBatch(request);

        assertThat(response.getTotal()).isEqualTo(3);
        assertThat(response.getInstances()).hasSize(1);
        assertThat(response.getInstances().get(0).isBatch()).isTrue();
        assertThat(response.getInstances().get(0).getBatchTotal()).isEqualTo(3);
        assertThat(response.getInstances().get(0).getBatchStatusCounts()).containsEntry("COMPLETED", 3L);
        assertThat(logStore).filteredOn(log -> "BATCH_SUBMITTED".equals(log.getAction())).hasSize(1);
        verify(notificationCenter).create(eq(requester), contains("3 hồ sơ"), anyString(),
                eq(response.getInstances().get(0).getRequestCode()),
                eq("/instances/" + response.getInstances().get(0).getId()));
    }

    @Test
    void batchRecordsAtSameReviewStepAreGroupedIntoOneTask() throws Exception {
        User requester = user("batch-requester@company.com"), reviewer = user("batch-reviewer@company.com");
        authenticated.set(requester);
        Workflow workflow = Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("Batch review")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();
        WorkflowStep start = step(workflow, StepType.START, "Start",
                mapper.writeValueAsString(Map.of("submissionMode", "BATCH", "maxBatchRows", 10)));
        WorkflowStep review = step(workflow, StepType.REVIEW, "Review",
                actorConfig(reviewer, Map.of("completionMode", "ANY", "resultMode", "REQUIRE_APPROVAL")));
        WorkflowStep end = step(workflow, StepType.END, "End",
                mapper.writeValueAsString(Map.of("outcome", "COMPLETED", "notifyRequester", false)));
        Map<UUID, List<WorkflowConnection>> graph = Map.of(
                start.getId(), List.of(connection(workflow, start, review, ConnectionType.DEFAULT)),
                review.getId(), List.of(connection(workflow, review, end, ConnectionType.REVIEW_PASS)));
        when(connections.findByFromStepId(any())).thenAnswer(call -> graph.getOrDefault(call.getArgument(0), List.of()));
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId())).thenReturn(List.of(start, review, end));
        when(steps.findById(review.getId())).thenReturn(Optional.of(review));
        when(authorization.canSubmit(workflow)).thenReturn(true);
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        CreateBatchInstanceRequest request = new CreateBatchInstanceRequest();
        request.setWorkflowId(workflow.getId());
        request.setRecords(List.of(Map.of(), Map.of(), Map.of()));

        BatchInstanceResponse submitted = engine.submitBatch(request);

        assertThat(submitted.getInstances()).hasSize(1);
        assertThat(taskStore).hasSize(1);
        authenticated.set(reviewer);
        when(currentUser.id()).thenReturn(reviewer.getId());
        assertThat(engine.myTask(taskStore.get(0).getId()).getBatchRecords()).hasSize(3);
        TaskActionRequest batchReview = new TaskActionRequest();
        batchReview.setReviewResults(List.of(new com.company.workflowbuilder.dto.ReviewResultItem("Tổng hợp", "Đủ 3 hồ sơ")));
        batchReview.setCalculatedOutputs(List.of(new com.company.workflowbuilder.dto.CalculatedOutput("amount", "Số tiền", "10 * 2"),
                new com.company.workflowbuilder.dto.CalculatedOutput("total", "Tổng tiền", "SUM([amount])")));
        var preview = engine.previewTaskOutputs(taskStore.get(0).getId(), batchReview);
        assertThat(preview).hasSize(3);
        assertThat(taskStore.get(0).getCalculatedResults()).isNull();
        InstanceResponse completed = engine.actTask(taskStore.get(0).getId(), "COMPLETE", batchReview);
        assertThat(engine.myTask(taskStore.get(0).getId()).getCalculatedRows()).allSatisfy(row -> assertThat(row).containsEntry("amount", 20).containsEntry("total", 60));
        assertThat(instanceStore.get().getFieldSnapshot()).contains("\"total\":60");
        assertThat(engine.myTask(taskStore.get(0).getId()).getReviewResults()).isEqualTo(batchReview.getReviewResults());
        assertThat(logStore).anyMatch(log -> log.getComment() != null && log.getComment().contains("Đủ 3 hồ sơ"));
        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(completed.getBatchStatusCounts()).containsEntry("COMPLETED", 3L);
    }

    @Test
    void reviewNotPassedCompletesTaskAndRejectsInstanceWhenNoRejectBranch() throws Exception {
        User requester=user("requester-review@company.com"),reviewer=user("reviewer@company.com");authenticated.set(requester);
        Workflow workflow=Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("Review flow").version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();
        WorkflowStep start=step(workflow,StepType.START,"Start","{}");
        WorkflowStep review=step(workflow,StepType.REVIEW,"Review",actorConfig(reviewer,Map.of("mode","MANUAL","commentRequired",true,"resultMode","REQUIRE_APPROVAL")));
        WorkflowStep end=step(workflow,StepType.END,"End",mapper.writeValueAsString(Map.of("outcome","COMPLETED")));
        Map<UUID,List<WorkflowConnection>> graph=Map.of(start.getId(),List.of(connection(workflow,start,review,ConnectionType.DEFAULT)),review.getId(),List.of(connection(workflow,review,end,ConnectionType.DEFAULT)));
        when(connections.findByFromStepId(any())).thenAnswer(invocation->graph.getOrDefault(invocation.getArgument(0),List.of()));
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId())).thenReturn(List.of(start,review,end));
        when(authorization.canSubmit(workflow)).thenReturn(true);when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        CreateInstanceRequest request=new CreateInstanceRequest();request.setWorkflowId(workflow.getId());request.setFields(new HashMap<>());
        InstanceResponse submitted=engine.submit(request);authenticated.set(reviewer);
        TaskActionRequest reviewResult=new TaskActionRequest();reviewResult.setComment("Không đạt yêu cầu");
        reviewResult.setReviewResults(List.of(new com.company.workflowbuilder.dto.ReviewResultItem("", "Thiếu chứng từ")));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> engine.act(submitted.getId(),"REJECT",reviewResult));
        assertThat(taskStore.get(0).getStatus()).isEqualTo(TaskStatus.PENDING);
        reviewResult.setReviewResults(List.of(new com.company.workflowbuilder.dto.ReviewResultItem("Cần bổ sung", "Thiếu chứng từ")));
        InstanceResponse rejected=engine.act(submitted.getId(),"REJECT",reviewResult);
        assertThat(taskStore.get(0).getReviewResults()).contains("Thiếu chứng từ");
        assertThat(logStore).anyMatch(log -> log.getComment() != null && log.getComment().contains("Cần bổ sung: Thiếu chứng từ"));
        assertThat(rejected.getStatus()).isEqualTo(InstanceStatus.REJECTED);
        assertThat(taskStore).hasSize(1).allMatch(task->task.getStatus()==TaskStatus.COMPLETED);
        verify(notificationCenter).create(eq(requester),eq("Yêu cầu không đạt review"),anyString(),eq(submitted.getRequestCode()),anyString());
    }

    @Test
    void reviewNotPassedFollowsConfiguredFailureBranch() throws Exception {
        User requester=user("requester-review-route@company.com"),reviewer=user("reviewer-route@company.com");authenticated.set(requester);
        Workflow workflow=Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("Review correction flow")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();
        WorkflowStep start=step(workflow,StepType.START,"Start","{}");
        WorkflowStep review=step(workflow,StepType.REVIEW,"Review",actorConfig(reviewer,Map.of("mode","MANUAL","commentRequired",true,"resultMode","REQUIRE_APPROVAL")));
        WorkflowStep correction=step(workflow,StepType.ASSIGNMENT,"Correct data",actorConfig(reviewer,Map.of("completionMode","ANY")));
        Map<UUID,List<WorkflowConnection>> graph=Map.of(
                start.getId(),List.of(connection(workflow,start,review,ConnectionType.DEFAULT)),
                review.getId(),List.of(connection(workflow,review,correction,ConnectionType.REVIEW_FAIL)));
        when(connections.findByFromStepId(any())).thenAnswer(invocation->graph.getOrDefault(invocation.getArgument(0),List.of()));
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId())).thenReturn(List.of(start,review,correction));
        when(authorization.canSubmit(workflow)).thenReturn(true);when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        CreateInstanceRequest request=new CreateInstanceRequest();request.setWorkflowId(workflow.getId());request.setFields(new HashMap<>());
        InstanceResponse submitted=engine.submit(request);authenticated.set(reviewer);
        TaskActionRequest reviewResult=new TaskActionRequest();reviewResult.setComment("Cần bổ sung dữ liệu");

        InstanceResponse routed=engine.act(submitted.getId(),"REJECT",reviewResult);

        assertThat(routed.getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(routed.getCurrentStepType()).isEqualTo("ASSIGNMENT");
        assertThat(taskStore).hasSize(2);
        assertThat(taskStore).extracting(WorkflowTask::getStatus).containsExactly(TaskStatus.COMPLETED,TaskStatus.PENDING);
        assertThat(logStore).extracting(InstanceStepLog::getAction).contains("REVIEW_NOT_PASSED","STEP_ACTIVATED");
    }

    @Test
    void manualApprovalRejectCanReturnForCorrectionAndCreateANewApprovalRound() throws Exception {
        User requester = user("requester-approval-return@company.com");
        User actor = user("actor-approval-return@company.com");
        authenticated.set(requester);
        Workflow workflow = Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID())
                .name("Approval correction flow").version("1.0").status(WorkflowStatus.PUBLISHED)
                .owner(requester).build();
        WorkflowStep start = step(workflow, StepType.START, "Start", "{}");
        WorkflowStep correction = step(workflow, StepType.ASSIGNMENT, "Bổ sung hồ sơ",
                actorConfig(actor, Map.of("completionMode", "ANY")));
        WorkflowStep approval = step(workflow, StepType.APPROVAL, "Phê duyệt",
                actorConfig(actor, Map.of("mode", "MANUAL", "completionMode", "ANY")));
        WorkflowStep end = step(workflow, StepType.END, "End",
                mapper.writeValueAsString(Map.of("outcome", "APPROVED", "notifyRequester", false)));
        Map<UUID, List<WorkflowConnection>> graph = Map.of(
                start.getId(), List.of(connection(workflow, start, approval, ConnectionType.DEFAULT)),
                correction.getId(), List.of(connection(workflow, correction, approval, ConnectionType.DEFAULT)),
                approval.getId(), List.of(
                        connection(workflow, approval, end, ConnectionType.APPROVE),
                        connection(workflow, approval, correction, ConnectionType.REJECT)));
        when(connections.findByFromStepId(any())).thenAnswer(call -> graph.getOrDefault(call.getArgument(0), List.of()));
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(steps.findByWorkflowIdOrderByPositionXAsc(workflow.getId()))
                .thenReturn(List.of(start, correction, approval, end));
        when(authorization.canSubmit(workflow)).thenReturn(true);
        when(users.findById(actor.getId())).thenReturn(Optional.of(actor));
        CreateInstanceRequest request = new CreateInstanceRequest();
        request.setWorkflowId(workflow.getId());
        request.setFields(new HashMap<>());

        InstanceResponse submitted = engine.submit(request);
        authenticated.set(actor);
        TaskActionRequest rejection = new TaskActionRequest();
        rejection.setComment("Vui lòng bổ sung chứng từ");
        InstanceResponse returned = engine.act(submitted.getId(), "REJECT", rejection);

        assertThat(returned.getStatus()).isEqualTo(InstanceStatus.RUNNING);
        assertThat(returned.getCurrentStepType()).isEqualTo("ASSIGNMENT");
        InstanceResponse resubmitted = engine.act(submitted.getId(), "COMPLETE", new TaskActionRequest());
        assertThat(resubmitted.getCurrentStepType()).isEqualTo("APPROVAL");
        assertThat(taskStore).extracting(WorkflowTask::getStatus)
                .containsExactly(TaskStatus.COMPLETED, TaskStatus.COMPLETED, TaskStatus.PENDING);
        assertThat(taskStore.get(0).getActivationId()).isNotEqualTo(taskStore.get(2).getActivationId());
    }

    @Test
    void requesterCanWithdrawRunningInstanceAndPendingTasksAreCancelled() {
        User requester=user("requester-withdraw@company.com"),owner=user("owner@company.com"),actor=user("actor-withdraw@company.com");
        authenticated.set(requester);when(currentUser.id()).thenReturn(requester.getId());
        Workflow workflow=Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("Withdraw flow")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(owner).build();
        WorkflowStep approval=step(workflow,StepType.APPROVAL,"Approval","{}");
        WorkflowInstance instance=WorkflowInstance.builder().id(UUID.randomUUID()).workflow(workflow).createdBy(requester)
                .currentStep(approval).status(InstanceStatus.RUNNING).fieldSnapshot("{}").startedAt(LocalDateTime.now()).build();
        instanceStore.set(instance);
        taskStore.add(WorkflowTask.builder().id(UUID.randomUUID()).instance(instance).step(approval).assignee(actor)
                .status(TaskStatus.PENDING).createdAt(LocalDateTime.now()).build());
        TaskActionRequest request=new TaskActionRequest();request.setComment("Nhập sai thông tin");

        InstanceResponse response=engine.withdraw(instance.getId(),request);

        assertThat(response.getStatus()).isEqualTo(InstanceStatus.WITHDRAWN);
        assertThat(response.getWithdrawalReason()).isEqualTo("Nhập sai thông tin");
        assertThat(taskStore).allMatch(task->task.getStatus()==TaskStatus.CANCELLED);
        assertThat(logStore).extracting(InstanceStepLog::getAction).contains("REQUEST_WITHDRAWN");
        verify(notificationCenter).create(eq(actor),eq("Yêu cầu đã được thu hồi"),anyString(),eq(instance.getRequestCode()),anyString());
    }

    @Test
    void percentagePolicyAdvancesOnlyAfterRoundedUpThreshold() throws Exception {
        User requester=user("requester-percent@company.com"), first=user("first@company.com"),
                second=user("second@company.com"), third=user("third@company.com");
        Workflow workflow=Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("Percentage flow")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();
        WorkflowStep assignment=step(workflow,StepType.ASSIGNMENT,"Parallel work",mapper.writeValueAsString(Map.of(
                "completionMode","PERCENTAGE","completionPercentage",50)));
        WorkflowStep end=step(workflow,StepType.END,"End",mapper.writeValueAsString(Map.of(
                "outcome","COMPLETED","notifyRequester",false)));
        WorkflowInstance instance=WorkflowInstance.builder().id(UUID.randomUUID()).workflow(workflow).createdBy(requester)
                .currentStep(assignment).status(InstanceStatus.RUNNING).fieldSnapshot("{}").startedAt(LocalDateTime.now()).build();
        instanceStore.set(instance);
        UUID activationId=UUID.randomUUID();
        for(User actor:List.of(first,second,third)) taskStore.add(WorkflowTask.builder().id(UUID.randomUUID())
                .instance(instance).step(assignment).assignee(actor).activationId(activationId)
                .status(TaskStatus.PENDING).createdAt(LocalDateTime.now()).build());
        when(connections.findByFromStepId(assignment.getId())).thenReturn(List.of(
                connection(workflow,assignment,end,ConnectionType.DEFAULT)));
        TaskActionRequest request=new TaskActionRequest();

        authenticated.set(first);
        InstanceResponse afterFirst=engine.act(instance.getId(),"COMPLETE",request);
        assertThat(afterFirst.getCurrentStepType()).isEqualTo("ASSIGNMENT");
        assertThat(taskStore).extracting(WorkflowTask::getStatus)
                .containsExactly(TaskStatus.COMPLETED,TaskStatus.PENDING,TaskStatus.PENDING);

        authenticated.set(second);
        InstanceResponse afterSecond=engine.act(instance.getId(),"COMPLETE",request);
        assertThat(afterSecond.getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(afterSecond.getCurrentStepType()).isEqualTo("END");
        assertThat(taskStore).extracting(WorkflowTask::getStatus)
                .containsExactly(TaskStatus.COMPLETED,TaskStatus.COMPLETED,TaskStatus.CANCELLED);
    }

    @Test
    void approvalAllPolicyWaitsForEveryFixedApprover() throws Exception {
        User requester=user("requester-all@company.com"), first=user("approver-one@company.com"),
                second=user("approver-two@company.com");
        Workflow workflow=Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("All approvals")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();
        WorkflowStep approval=step(workflow,StepType.APPROVAL,"Approve together",mapper.writeValueAsString(Map.of(
                "mode","MANUAL","completionMode","ALL")));
        WorkflowStep end=step(workflow,StepType.END,"End",mapper.writeValueAsString(Map.of(
                "outcome","APPROVED","notifyRequester",false)));
        WorkflowInstance instance=WorkflowInstance.builder().id(UUID.randomUUID()).workflow(workflow).createdBy(requester)
                .currentStep(approval).status(InstanceStatus.RUNNING).fieldSnapshot("{}").startedAt(LocalDateTime.now()).build();
        instanceStore.set(instance);
        UUID activationId=UUID.randomUUID();
        for(User actor:List.of(first,second)) taskStore.add(WorkflowTask.builder().id(UUID.randomUUID())
                .instance(instance).step(approval).assignee(actor).activationId(activationId)
                .status(TaskStatus.PENDING).createdAt(LocalDateTime.now()).build());
        when(connections.findByFromStepId(approval.getId())).thenReturn(List.of(
                connection(workflow,approval,end,ConnectionType.APPROVE)));
        TaskActionRequest request=new TaskActionRequest();

        authenticated.set(first);
        assertThat(engine.act(instance.getId(),"APPROVE",request).getCurrentStepType()).isEqualTo("APPROVAL");
        authenticated.set(second);
        InstanceResponse completed=engine.act(instance.getId(),"APPROVE",request);

        assertThat(completed.getStatus()).isEqualTo(InstanceStatus.APPROVED);
        assertThat(taskStore).allMatch(task -> task.getStatus() == TaskStatus.COMPLETED);
    }

    @Test
    void multipleReviewersCanComputeTheSameOutputWithoutLosingTheirOwnResults() throws Exception {
        User requester = user("owner-calc@company.com"), first = user("first-calc@company.com"), second = user("second-calc@company.com");
        Workflow workflow = Workflow.builder().id(UUID.randomUUID()).familyId(UUID.randomUUID()).name("Calculation review")
                .version("1.0").status(WorkflowStatus.PUBLISHED).owner(requester).build();
        WorkflowStep review = step(workflow, StepType.REVIEW, "Review", mapper.writeValueAsString(Map.of("completionMode", "ALL")));
        WorkflowStep end = step(workflow, StepType.END, "End", mapper.writeValueAsString(Map.of("outcome", "COMPLETED", "notifyRequester", false)));
        WorkflowInstance instance = WorkflowInstance.builder().id(UUID.randomUUID()).workflow(workflow).createdBy(requester)
                .currentStep(review).status(InstanceStatus.RUNNING).fieldSnapshot("{\"amount\":10}").startedAt(LocalDateTime.now()).build();
        instanceStore.set(instance);
        UUID activation = UUID.randomUUID();
        for (User actor : List.of(first, second)) taskStore.add(WorkflowTask.builder().id(UUID.randomUUID())
                .instance(instance).step(review).assignee(actor).activationId(activation).status(TaskStatus.PENDING).createdAt(LocalDateTime.now()).build());
        when(connections.findByFromStepId(review.getId())).thenReturn(List.of(connection(workflow, review, end, ConnectionType.REVIEW_PASS)));
        TaskActionRequest request = new TaskActionRequest();
        request.setCalculatedOutputs(List.of(new com.company.workflowbuilder.dto.CalculatedOutput("total", "Total", "[amount] * 2")));
        authenticated.set(first);
        assertThat(engine.act(instance.getId(), "COMPLETE", request).getCurrentStepType()).isEqualTo("REVIEW");
        request.setCalculatedOutputs(List.of(new com.company.workflowbuilder.dto.CalculatedOutput("total", "Total", "[amount] * 3")));
        authenticated.set(second);
        assertThat(engine.act(instance.getId(), "COMPLETE", request).getStatus()).isEqualTo(InstanceStatus.COMPLETED);
        assertThat(taskStore.get(0).getCalculatedResults()).contains("\"total\":20");
        assertThat(taskStore.get(1).getCalculatedResults()).contains("\"total\":30");
    }

    private User user(String email){return User.builder().id(UUID.randomUUID()).email(email).passwordHash("x").displayName(email).active(true).build();}
    private WorkflowStep step(Workflow workflow,StepType type,String label,String config){return WorkflowStep.builder().id(UUID.randomUUID()).workflow(workflow).type(type).label(label).configJson(config).build();}
    private String actorConfig(User actor,Map<String,Object> extra) throws Exception {Map<String,Object> value=new HashMap<>(extra);value.put("approverMode","FIXED_USER");value.put("actorUserIds",List.of(actor.getId().toString()));return mapper.writeValueAsString(value);}
    private WorkflowConnection connection(Workflow workflow,WorkflowStep from,WorkflowStep to,ConnectionType type){return WorkflowConnection.builder().id(UUID.randomUUID()).workflow(workflow).fromStep(from).toStep(to).type(type).build();}
}
