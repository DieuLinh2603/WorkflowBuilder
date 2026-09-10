package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.workflow.ConnectionType;
import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowConnection;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.WorkflowConnectionRepository;
import com.company.workflowbuilder.repository.WorkflowRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkflowValidationBranchTest {

    private final WorkflowRepository workflows = mock(WorkflowRepository.class);
    private final WorkflowStepRepository steps = mock(WorkflowStepRepository.class);
    private final WorkflowConnectionRepository connections = mock(WorkflowConnectionRepository.class);
    private final CustomFieldDefinitionRepository fields = mock(CustomFieldDefinitionRepository.class);
    private final WorkflowAuthorizationService authorization = mock(WorkflowAuthorizationService.class);
    private final WorkflowValidationService validation = new WorkflowValidationService(
            workflows, steps, connections, fields, authorization, new ObjectMapper());

    private UUID workflowId;
    private Workflow workflow;
    private WorkflowStep start;
    private WorkflowStep approval;
    private WorkflowStep success;
    private WorkflowStep fallback;

    @BeforeEach
    void setUp() {
        workflowId = UUID.randomUUID();
        workflow = Workflow.builder().id(workflowId).name("IF validation").build();
        start = step(StepType.START, "Start");
        approval = step(StepType.APPROVAL, "Approval");
        success = step(StepType.END, "Success");
        fallback = step(StepType.END, "Fallback");

        when(workflows.findById(workflowId)).thenReturn(Optional.of(workflow));
        when(steps.findByWorkflowIdOrderByPositionXAsc(workflowId))
                .thenReturn(List.of(start, approval, success, fallback));
        when(fields.findByStepWorkflowId(workflowId)).thenReturn(List.of());
    }

    @Test
    void requiresElseWhenStepHasIfBranch() {
        when(connections.findByWorkflowId(workflowId)).thenReturn(List.of(
                connection(start, approval, ConnectionType.DEFAULT),
                connection(approval, success, ConnectionType.IF)));

        assertThat(validation.validate(workflowId))
                .anyMatch(error -> error.contains("IF") && error.contains("ELSE"));
    }

    @Test
    void acceptsIfStepWhenElseBranchExists() {
        when(connections.findByWorkflowId(workflowId)).thenReturn(List.of(
                connection(start, approval, ConnectionType.DEFAULT),
                connection(approval, success, ConnectionType.IF),
                connection(approval, fallback, ConnectionType.ELSE)));

        assertThat(validation.validate(workflowId))
                .noneMatch(error -> error.contains("IF") && error.contains("bắt buộc") && error.contains("ELSE"));
    }

    private WorkflowStep step(StepType type, String label) {
        return WorkflowStep.builder()
                .id(UUID.randomUUID())
                .workflow(workflow)
                .type(type)
                .label(label)
                .configJson("{}")
                .build();
    }

    private WorkflowConnection connection(WorkflowStep from, WorkflowStep to, ConnectionType type) {
        return WorkflowConnection.builder()
                .id(UUID.randomUUID())
                .workflow(workflow)
                .fromStep(from)
                .toStep(to)
                .type(type)
                .build();
    }
}
