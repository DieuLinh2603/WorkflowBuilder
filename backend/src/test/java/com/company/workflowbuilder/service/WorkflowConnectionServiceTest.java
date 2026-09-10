package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.ConnectionUpsertRequest;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.field.FieldType;
import com.company.workflowbuilder.repository.WorkflowConnectionRepository;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.WorkflowRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowConnectionServiceTest {
    private WorkflowRepository workflows;
    private WorkflowStepRepository steps;
    private WorkflowConnectionRepository connections;
    private CustomFieldDefinitionRepository fields;
    private WorkflowConnectionService service;

    @BeforeEach
    void setUp() {
        workflows = mock(WorkflowRepository.class); steps = mock(WorkflowStepRepository.class);
        connections = mock(WorkflowConnectionRepository.class);
        fields = mock(CustomFieldDefinitionRepository.class);
        service = new WorkflowConnectionService(workflows, steps, connections,
                fields, mock(WorkflowAuthorizationService.class));
        when(connections.save(any())).thenAnswer(invocation -> {
            WorkflowConnection connection = invocation.getArgument(0);
            if (connection.getId() == null) connection.setId(UUID.randomUUID());
            return connection;
        });
    }

    @Test
    void createsApprovalBranchAndRejectsDuplicateType() {
        Workflow workflow = workflow();
        WorkflowStep approval = step(workflow, StepType.APPROVAL);
        WorkflowStep target = step(workflow, StepType.END);
        mockLookup(workflow, approval, target);
        ConnectionUpsertRequest request = request(approval, target, ConnectionType.APPROVE);

        assertEquals(ConnectionType.APPROVE, service.create(workflow.getId(), request).getType());

        WorkflowConnection existing = WorkflowConnection.builder().id(UUID.randomUUID()).workflow(workflow)
                .fromStep(approval).toStep(target).type(ConnectionType.APPROVE).build();
        when(connections.findByFromStepId(approval.getId())).thenReturn(List.of(existing));
        assertThrows(IllegalArgumentException.class, () -> service.create(workflow.getId(), request));
    }

    @Test
    void rejectsDefaultFromApprovalAndApprovalTypeFromNormalStep() {
        Workflow workflow = workflow();
        WorkflowStep approval = step(workflow, StepType.APPROVAL);
        WorkflowStep review = step(workflow, StepType.REVIEW);
        WorkflowStep target = step(workflow, StepType.END);
        mockLookup(workflow, approval, review, target);

        assertThrows(IllegalArgumentException.class,
                () -> service.create(workflow.getId(), request(approval, target, ConnectionType.DEFAULT)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(workflow.getId(), request(review, target, ConnectionType.REJECT)));
    }

    @Test
    void createsDistinctReviewResultBranchesAndRejectsDuplicates() {
        Workflow workflow = workflow();
        WorkflowStep review = step(workflow, StepType.REVIEW);
        WorkflowStep passTarget = step(workflow, StepType.ASSIGNMENT);
        WorkflowStep failTarget = step(workflow, StepType.END);
        mockLookup(workflow, review, passTarget, failTarget);

        assertEquals(ConnectionType.REVIEW_PASS,
                service.create(workflow.getId(), request(review, passTarget, ConnectionType.REVIEW_PASS)).getType());
        assertEquals(ConnectionType.REVIEW_FAIL,
                service.create(workflow.getId(), request(review, failTarget, ConnectionType.REVIEW_FAIL)).getType());

        WorkflowConnection existing = WorkflowConnection.builder().id(UUID.randomUUID()).workflow(workflow)
                .fromStep(review).toStep(passTarget).type(ConnectionType.REVIEW_PASS).build();
        when(connections.findByFromStepId(review.getId())).thenReturn(List.of(existing));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(workflow.getId(), request(review, failTarget, ConnectionType.REVIEW_PASS)));
        assertThrows(IllegalArgumentException.class,
                () -> service.create(workflow.getId(), request(review, failTarget, ConnectionType.DEFAULT)));
    }

    @Test
    void reclassifiesLegacyApprovalDefault() {
        Workflow workflow = workflow();
        WorkflowStep approval = step(workflow, StepType.APPROVAL);
        WorkflowStep target = step(workflow, StepType.END);
        WorkflowConnection legacy = WorkflowConnection.builder().id(UUID.randomUUID()).workflow(workflow)
                .fromStep(approval).toStep(target).type(ConnectionType.DEFAULT)
                .logicalOperator(LogicalOperator.AND).clauses(new ArrayList<>()).build();
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(connections.findById(legacy.getId())).thenReturn(Optional.of(legacy));
        when(connections.findByFromStepId(approval.getId())).thenReturn(List.of(legacy));

        assertEquals(ConnectionType.REJECT,
                service.update(workflow.getId(), legacy.getId(), request(approval, target, ConnectionType.REJECT)).getType());
    }

    @Test
    void createsIfWithMultipleClausesFromWorkflowFields() {
        Workflow workflow = workflow();
        WorkflowStep start = step(workflow, StepType.START);
        WorkflowStep target = step(workflow, StepType.REVIEW);
        mockLookup(workflow, start, target);
        when(fields.findByStepWorkflowId(workflow.getId())).thenReturn(List.of(
                CustomFieldDefinition.builder().step(start).fieldKey("amount").label("Tổng tiền").type(FieldType.NUMBER).build(),
                CustomFieldDefinition.builder().step(start).fieldKey("department").label("Phòng ban").type(FieldType.TEXT).build()));

        ConnectionUpsertRequest request = request(start, target, ConnectionType.IF);
        request.setLogicalOperator(LogicalOperator.AND);
        request.setClauses(new ArrayList<>());
        request.getClauses().add(clause("amount", ConditionOperator.GT, "100000000"));
        request.getClauses().add(clause("department", ConditionOperator.EQ, "Finance"));

        assertEquals(2, service.create(workflow.getId(), request).getClauses().size());
    }

    @Test
    void rejectsOperatorThatDoesNotMatchFieldType() {
        Workflow workflow = workflow();
        WorkflowStep start = step(workflow, StepType.START);
        WorkflowStep target = step(workflow, StepType.REVIEW);
        mockLookup(workflow, start, target);
        when(fields.findByStepWorkflowId(workflow.getId())).thenReturn(List.of(
                CustomFieldDefinition.builder().step(start).fieldKey("title").label("Tiêu đề").type(FieldType.TEXT).build()));
        ConnectionUpsertRequest request = request(start, target, ConnectionType.IF);
        request.setClauses(new ArrayList<>(List.of(clause("title", ConditionOperator.GT, "10"))));

        assertThrows(IllegalArgumentException.class, () -> service.create(workflow.getId(), request));
    }

    @Test
    void allowsMultipleIfBranchesForDynamicClassification() {
        Workflow workflow = workflow();
        WorkflowStep start = step(workflow, StepType.START);
        WorkflowStep gradeA = step(workflow, StepType.END);
        WorkflowStep gradeB = step(workflow, StepType.END);
        mockLookup(workflow, start, gradeA, gradeB);
        CustomFieldDefinition projects = CustomFieldDefinition.builder().step(start).fieldKey("project_count")
                .label("Số dự án").type(FieldType.NUMBER).build();
        when(fields.findByStepWorkflowId(workflow.getId())).thenReturn(List.of(projects));
        WorkflowConnection existing = WorkflowConnection.builder().id(UUID.randomUUID()).workflow(workflow)
                .fromStep(start).toStep(gradeA).type(ConnectionType.IF).build();
        when(connections.findByFromStepId(start.getId())).thenReturn(List.of(existing));
        ConnectionUpsertRequest second = request(start, gradeB, ConnectionType.IF);
        second.setClauses(new ArrayList<>(List.of(clause("project_count", ConditionOperator.GTE, "1"))));

        assertDoesNotThrow(() -> service.create(workflow.getId(), second));
    }

    @Test
    void storesCombinedBasicAndCalculationExpression() throws Exception {
        Workflow workflow = workflow();
        WorkflowStep start = step(workflow, StepType.START);
        WorkflowStep target = step(workflow, StepType.END);
        mockLookup(workflow, start, target);
        when(fields.findByStepWorkflowId(workflow.getId())).thenReturn(List.of(
                CustomFieldDefinition.builder().step(start).fieldKey("department").label("Phòng ban").type(FieldType.TEXT).build(),
                CustomFieldDefinition.builder().step(start).fieldKey("revenue").label("Doanh thu").type(FieldType.NUMBER).build(),
                CustomFieldDefinition.builder().step(start).fieldKey("cost").label("Chi phí").type(FieldType.NUMBER).build()));

        String json = """
                {"type":"AND","builderMode":"COMBINED_CONDITION","children":[
                  {"type":"AND","builderMode":"CONDITION_GROUPS","children":[
                    {"type":"AND","children":[
                      {"type":"COMPARE","operator":"EQ","left":{"type":"FIELD","fieldKey":"department"},"right":{"type":"VALUE","value":"IT"}}]}]},
                  {"type":"COMPARE","operator":"GT",
                   "left":{"type":"SUBTRACT","operands":[
                     {"type":"FIELD","fieldKey":"revenue"},{"type":"FIELD","fieldKey":"cost"}]},
                   "right":{"type":"VALUE","value":50}}
                ]}
                """;
        ConnectionUpsertRequest request = request(start, target, ConnectionType.IF);
        ConnectionUpsertRequest.Clause expressionClause = new ConnectionUpsertRequest.Clause();
        expressionClause.setExpression(new ObjectMapper().readValue(json, java.util.Map.class));
        request.setClauses(new ArrayList<>(List.of(expressionClause)));

        var response = service.create(workflow.getId(), request);

        assertEquals("COMBINED_CONDITION", response.getClauses().get(0).getExpression().get("builderMode"));
    }

    private void mockLookup(Workflow workflow, WorkflowStep... workflowSteps) {
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        for (WorkflowStep step : workflowSteps) when(steps.findById(step.getId())).thenReturn(Optional.of(step));
    }

    private Workflow workflow() { return Workflow.builder().id(UUID.randomUUID()).name("WF").status(WorkflowStatus.DRAFT).build(); }
    private WorkflowStep step(Workflow workflow, StepType type) { return WorkflowStep.builder().id(UUID.randomUUID()).workflow(workflow).type(type).label(type.name()).build(); }
    private ConnectionUpsertRequest request(WorkflowStep from, WorkflowStep to, ConnectionType type) {
        ConnectionUpsertRequest request = new ConnectionUpsertRequest(); request.setFromStepId(from.getId());
        request.setToStepId(to.getId()); request.setType(type); request.setLogicalOperator(LogicalOperator.AND);
        return request;
    }
    private ConnectionUpsertRequest.Clause clause(String fieldKey, ConditionOperator operator, String value) {
        ConnectionUpsertRequest.Clause clause = new ConnectionUpsertRequest.Clause();
        clause.setFieldKey(fieldKey); clause.setOperator(operator); clause.setExpectedValue(value);
        return clause;
    }
}
