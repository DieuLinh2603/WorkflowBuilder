package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.response.StepDeleteResponse;
import com.company.workflowbuilder.dto.request.StepLayoutUpdateRequest;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.workflow.WorkflowDefinitionAnalysis;
import com.company.workflowbuilder.service.workflow.WorkflowViewMapper;
import com.company.workflowbuilder.service.workflow.WorkflowQueryUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowServiceDeleteStepTest {
    private WorkflowStepRepository steps;
    private WorkflowRepository workflows;
    private WorkflowConnectionRepository connections;
    private WorkflowAuthorizationService authorization;
    private WorkflowService service;

    @BeforeEach
    void setUp() {
        workflows = mock(WorkflowRepository.class);
        steps = mock(WorkflowStepRepository.class);
        connections = mock(WorkflowConnectionRepository.class);
        authorization = mock(WorkflowAuthorizationService.class);
        CustomFieldDefinitionRepository fields = mock(CustomFieldDefinitionRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        service = new WorkflowService(workflows, steps, mock(UserRepository.class), connections,
                fields, currentUser, authorization,
                mock(WorkflowValidationService.class), mock(WorkflowAudienceRepository.class),
                mock(WorkflowInstanceRepository.class), new WorkflowViewMapper(authorization, currentUser),
                new WorkflowDefinitionAnalysis(steps, fields, connections), mock(WorkflowQueryUseCase.class));
        when(connections.save(any())).thenAnswer(invocation -> {
            WorkflowConnection connection = invocation.getArgument(0);
            if (connection.getId() == null) connection.setId(UUID.randomUUID());
            return connection;
        });
    }

    @Test
    void reconnectsLinearFlowAndPreservesIncomingCondition() {
        Workflow workflow = workflow(WorkflowStatus.DRAFT);
        WorkflowStep before = step(workflow, StepType.REVIEW);
        WorkflowStep middle = step(workflow, StepType.NOTIFICATION);
        WorkflowStep after = step(workflow, StepType.END);
        WorkflowConnection incoming = connection(workflow, before, middle, ConnectionType.IF);
        incoming.getClauses().add(WorkflowConditionClause.builder().connection(incoming).fieldKey("amount")
                .operator(ConditionOperator.GT).expectedValue("100").displayOrder(0).build());
        WorkflowConnection outgoing = connection(workflow, middle, after, ConnectionType.DEFAULT);
        when(steps.findById(middle.getId())).thenReturn(Optional.of(middle));
        when(connections.findByWorkflowId(workflow.getId())).thenReturn(List.of(incoming, outgoing));

        StepDeleteResponse result = service.deleteStep(workflow.getId(), middle.getId(), incoming.getId(), outgoing.getId());

        assertEquals(middle.getId(), result.getDeletedStepId());
        assertEquals(ConnectionType.IF, result.getReplacementConnection().getType());
        assertEquals(before.getId(), result.getReplacementConnection().getFromStepId());
        assertEquals(after.getId(), result.getReplacementConnection().getToStepId());
        assertEquals("amount", result.getReplacementConnection().getClauses().get(0).getFieldKey());
        verify(connections).deleteAll(List.of(incoming, outgoing));
        verify(steps).delete(middle);
    }

    @Test
    void disconnectDeletesEveryAffectedBranchWithoutReplacement() {
        Workflow workflow = workflow(WorkflowStatus.DRAFT);
        WorkflowStep middle = step(workflow, StepType.APPROVAL);
        WorkflowConnection first = connection(workflow, step(workflow, StepType.START), middle, ConnectionType.DEFAULT);
        WorkflowConnection approve = connection(workflow, middle, step(workflow, StepType.END), ConnectionType.APPROVE);
        WorkflowConnection reject = connection(workflow, middle, step(workflow, StepType.END), ConnectionType.REJECT);
        when(steps.findById(middle.getId())).thenReturn(Optional.of(middle));
        when(connections.findByWorkflowId(workflow.getId())).thenReturn(List.of(first, approve, reject));

        StepDeleteResponse result = service.deleteStep(workflow.getId(), middle.getId(), null, null);

        assertNull(result.getReplacementConnection());
        assertEquals(3, result.getDeletedConnectionIds().size());
        verify(connections).deleteAll(List.of(first, approve, reject));
    }

    @Test
    void rejectsStartAndNonDraftWithoutMutatingConnections() {
        Workflow draft = workflow(WorkflowStatus.DRAFT);
        WorkflowStep start = step(draft, StepType.START);
        when(steps.findById(start.getId())).thenReturn(Optional.of(start));
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteStep(draft.getId(), start.getId(), null, null));

        Workflow published = workflow(WorkflowStatus.PUBLISHED);
        WorkflowStep review = step(published, StepType.REVIEW);
        when(steps.findById(review.getId())).thenReturn(Optional.of(review));
        assertThrows(IllegalArgumentException.class,
                () -> service.deleteStep(published.getId(), review.getId(), null, null));
        verify(connections, never()).deleteAll(any());
    }

    @Test
    void rejectsConnectionThatDoesNotTerminateAtDeletedStep() {
        Workflow workflow = workflow(WorkflowStatus.DRAFT);
        WorkflowStep middle = step(workflow, StepType.REVIEW);
        WorkflowConnection unrelated = connection(workflow, step(workflow, StepType.START), step(workflow, StepType.END), ConnectionType.DEFAULT);
        WorkflowConnection outgoing = connection(workflow, middle, step(workflow, StepType.END), ConnectionType.DEFAULT);
        when(steps.findById(middle.getId())).thenReturn(Optional.of(middle));
        when(connections.findByWorkflowId(workflow.getId())).thenReturn(List.of(unrelated, outgoing));

        assertThrows(IllegalArgumentException.class,
                () -> service.deleteStep(workflow.getId(), middle.getId(), unrelated.getId(), outgoing.getId()));
        verify(connections, never()).deleteAll(any());
        verify(steps, never()).delete(any());
    }

    @Test
    void reusesEquivalentConnectionInsteadOfCreatingDuplicate() {
        Workflow workflow = workflow(WorkflowStatus.DRAFT);
        WorkflowStep before = step(workflow, StepType.START);
        WorkflowStep middle = step(workflow, StepType.REVIEW);
        WorkflowStep after = step(workflow, StepType.END);
        WorkflowConnection incoming = connection(workflow, before, middle, ConnectionType.DEFAULT);
        WorkflowConnection outgoing = connection(workflow, middle, after, ConnectionType.DEFAULT);
        WorkflowConnection existing = connection(workflow, before, after, ConnectionType.DEFAULT);
        when(steps.findById(middle.getId())).thenReturn(Optional.of(middle));
        when(connections.findByWorkflowId(workflow.getId())).thenReturn(List.of(incoming, outgoing, existing));

        StepDeleteResponse result = service.deleteStep(workflow.getId(), middle.getId(), incoming.getId(), outgoing.getId());

        assertEquals(existing.getId(), result.getReplacementConnection().getId());
        verify(connections, never()).save(any());
        verify(connections).deleteAll(List.of(incoming, outgoing));
    }

    @Test
    void savesAllLayoutPositions() {
        Workflow workflow = workflow(WorkflowStatus.DRAFT);
        WorkflowStep first = step(workflow, StepType.START);
        WorkflowStep second = step(workflow, StepType.REVIEW);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(steps.findById(first.getId())).thenReturn(Optional.of(first));
        when(steps.findById(second.getId())).thenReturn(Optional.of(second));
        when(steps.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        StepLayoutUpdateRequest request = new StepLayoutUpdateRequest();
        request.setPositions(List.of(position(first.getId(), 101, 202), position(second.getId(), -30, 410)));

        assertEquals(2, service.updateLayout(workflow.getId(), request).size());
        assertEquals(101, first.getPositionX()); assertEquals(202, first.getPositionY());
        assertEquals(-30, second.getPositionX()); assertEquals(410, second.getPositionY());
        verify(steps).saveAll(any());
    }

    private Workflow workflow(WorkflowStatus status) {
        return Workflow.builder().id(UUID.randomUUID()).name("WF").status(status).build();
    }

    private WorkflowStep step(Workflow workflow, StepType type) {
        return WorkflowStep.builder().id(UUID.randomUUID()).workflow(workflow).type(type).label(type.name()).build();
    }

    private WorkflowConnection connection(Workflow workflow, WorkflowStep from, WorkflowStep to, ConnectionType type) {
        return WorkflowConnection.builder().id(UUID.randomUUID()).workflow(workflow).fromStep(from).toStep(to)
                .type(type).logicalOperator(LogicalOperator.AND).clauses(new ArrayList<>()).build();
    }

    private StepLayoutUpdateRequest.Position position(UUID stepId, int x, int y) {
        StepLayoutUpdateRequest.Position position = new StepLayoutUpdateRequest.Position();
        position.setStepId(stepId); position.setPositionX(x); position.setPositionY(y); return position;
    }
}
