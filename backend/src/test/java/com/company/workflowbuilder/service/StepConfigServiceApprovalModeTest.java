package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.ApprovalConfigRequest;
import com.company.workflowbuilder.dto.request.ConnectionUpsertRequest;
import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import com.company.workflowbuilder.entity.field.FieldType;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StepConfigServiceApprovalModeTest {
    private WorkflowStepRepository steps;
    private CustomFieldDefinitionRepository fields;
    private UserRepository users;
    private StepConfigService service;
    private Workflow workflow;
    private WorkflowStep approval;

    @BeforeEach
    void setUp() {
        steps = mock(WorkflowStepRepository.class);
        fields = mock(CustomFieldDefinitionRepository.class);
        users = mock(UserRepository.class);
        service = new StepConfigService(steps, users, fields,
                mock(WorkflowAuthorizationService.class), new ObjectMapper());
        workflow = Workflow.builder().id(UUID.randomUUID()).name("Purchase")
                .status(WorkflowStatus.DRAFT).familyId(UUID.randomUUID()).build();
        approval = WorkflowStep.builder().id(UUID.randomUUID()).workflow(workflow)
                .type(StepType.APPROVAL).label("Approve purchase").build();
        when(steps.findById(approval.getId())).thenReturn(Optional.of(approval));
        when(steps.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void savesCalculationActionAndRejectsInvalidFormula() {
        approval.setType(StepType.SYSTEM_ACTION);
        var request = new com.company.workflowbuilder.dto.request.SystemActionConfigRequest();
        request.setActionType(com.company.workflowbuilder.dto.request.SystemActionConfigRequest.ActionType.CALCULATE_OUTPUT);
        request.setCalculatedOutputs(List.of(new com.company.workflowbuilder.dto.CalculatedOutput("total", "Tổng", "SUM([amount])")));
        var config = service.configureSystemAction(workflow.getId(), approval.getId(), request);
        assertEquals("CALCULATE_OUTPUT", config.get("actionType"));
        assertTrue(approval.getConfigJson().contains("SUM([amount])"));
        request.setCalculatedOutputs(List.of(new com.company.workflowbuilder.dto.CalculatedOutput("total", "Tổng", "[amount] +")));
        assertThrows(IllegalArgumentException.class, () -> service.configureSystemAction(workflow.getId(), approval.getId(), request));
    }

    @Test
    void savesReviewCalculationDefaults() {
        approval.setType(StepType.REVIEW);
        var request = new com.company.workflowbuilder.dto.request.ReviewConfigRequest();
        request.setApproverMode(ApprovalConfigRequest.ApproverMode.DYNAMIC);
        request.setDynamicActorSource(ApprovalConfigRequest.DynamicActorSource.REQUEST_CREATOR_MANAGER);
        request.setCalculatedOutputs(List.of(new com.company.workflowbuilder.dto.CalculatedOutput("total", "Tổng", "[amount] * 2")));
        service.configureReview(workflow.getId(), approval.getId(), request);
        assertTrue(approval.getConfigJson().contains("[amount] * 2"));
    }

    @Test
    void savesManualApprovalWithDynamicApprover() {
        ApprovalConfigRequest request = new ApprovalConfigRequest();
        request.setMode(ApprovalConfigRequest.Mode.MANUAL);
        request.setApproverMode(ApprovalConfigRequest.ApproverMode.DYNAMIC);
        request.setDynamicActorSource(ApprovalConfigRequest.DynamicActorSource.REQUEST_CREATOR_MANAGER);

        Map<String, Object> saved = service.configureApproval(workflow.getId(), approval.getId(), request);

        assertEquals("MANUAL", saved.get("mode"));
        assertTrue(approval.getConfigJson().contains("\"mode\":\"MANUAL\""));
    }

    @Test
    void savesAutoApprovalConditions() {
        WorkflowStep start = WorkflowStep.builder().id(UUID.randomUUID()).workflow(workflow).type(StepType.START).build();
        CustomFieldDefinition amount = CustomFieldDefinition.builder().id(UUID.randomUUID()).step(start)
                .fieldKey("total_amount").label("Total amount").type(FieldType.NUMBER).build();
        when(fields.findByStepWorkflowId(workflow.getId())).thenReturn(List.of(amount));
        ApprovalConfigRequest request = autoRequest("total_amount", ConditionOperator.LTE, "5000000");

        Map<String, Object> saved = service.configureApproval(workflow.getId(), approval.getId(), request);

        assertEquals("AUTO", saved.get("mode"));
        assertTrue(approval.getConfigJson().contains("\"fieldKey\":\"total_amount\""));
        verify(steps).save(approval);
    }

    @Test
    void savesMultipleFixedApproversWithoutCollapsingTheSelection() {
        User first = User.builder().id(UUID.randomUUID()).active(true).email("first@company.com").build();
        User second = User.builder().id(UUID.randomUUID()).active(true).email("second@company.com").build();
        when(users.findById(first.getId())).thenReturn(Optional.of(first));
        when(users.findById(second.getId())).thenReturn(Optional.of(second));
        ApprovalConfigRequest request = new ApprovalConfigRequest();
        request.setMode(ApprovalConfigRequest.Mode.MANUAL);
        request.setApproverMode(ApprovalConfigRequest.ApproverMode.FIXED_USER);
        request.setActorUserIds(new ArrayList<>(List.of(first.getId(), second.getId())));

        Map<String, Object> saved = service.configureApproval(workflow.getId(), approval.getId(), request);

        assertEquals(List.of(first.getId().toString(), second.getId().toString()),
                ((List<?>) saved.get("actorUserIds")).stream().map(Object::toString).toList());
        assertEquals("", saved.get("fixedUserEmail"));
    }

    @Test
    void rejectsOperatorThatDoesNotMatchFieldType() {
        WorkflowStep start = WorkflowStep.builder().id(UUID.randomUUID()).workflow(workflow).type(StepType.START).build();
        CustomFieldDefinition attachment = CustomFieldDefinition.builder().id(UUID.randomUUID()).step(start)
                .fieldKey("quotation").label("Quotation").type(FieldType.FILE).build();
        when(fields.findByStepWorkflowId(workflow.getId())).thenReturn(List.of(attachment));
        ApprovalConfigRequest request = autoRequest("quotation", ConditionOperator.GT, "1");

        assertThrows(IllegalArgumentException.class,
                () -> service.configureApproval(workflow.getId(), approval.getId(), request));
        verify(steps, never()).save(any());
    }

    private ApprovalConfigRequest autoRequest(String fieldKey, ConditionOperator operator, String expectedValue) {
        ConnectionUpsertRequest.Clause condition = new ConnectionUpsertRequest.Clause();
        condition.setFieldKey(fieldKey);
        condition.setOperator(operator);
        condition.setExpectedValue(expectedValue);
        ApprovalConfigRequest request = new ApprovalConfigRequest();
        request.setMode(ApprovalConfigRequest.Mode.AUTO);
        request.setLogicalOperator(LogicalOperator.AND);
        request.setAutoConditions(new ArrayList<>(List.of(condition)));
        return request;
    }
}
