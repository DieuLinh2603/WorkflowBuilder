package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.workflow.ConnectionType;
import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.entity.workflow.WorkflowConnection;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.WorkflowConnectionRepository;
import com.company.workflowbuilder.repository.WorkflowRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class WorkflowValidationCycleTest {

    private final WorkflowValidationService validation = new WorkflowValidationService(
            mock(WorkflowRepository.class),
            mock(WorkflowStepRepository.class),
            mock(WorkflowConnectionRepository.class),
            mock(CustomFieldDefinitionRepository.class),
            mock(WorkflowAuthorizationService.class),
            new ObjectMapper());

    @Test
    void allowsManualApprovalRejectToReturnToPreviousStep() {
        WorkflowStep correction = step(StepType.ASSIGNMENT, "{}");
        WorkflowStep approval = step(StepType.APPROVAL, "{\"mode\":\"MANUAL\"}");

        boolean hasCycle = validation.hasCycle(
                List.of(correction, approval),
                List.of(
                        connection(correction, approval, ConnectionType.DEFAULT),
                        connection(approval, correction, ConnectionType.REJECT)));

        assertThat(hasCycle).isFalse();
    }

    @Test
    void stillRejectsAutomaticApprovalLoop() {
        WorkflowStep processing = step(StepType.SYSTEM_ACTION, "{}");
        WorkflowStep approval = step(StepType.APPROVAL, "{\"mode\":\"AUTO\"}");

        boolean hasCycle = validation.hasCycle(
                List.of(processing, approval),
                List.of(
                        connection(processing, approval, ConnectionType.DEFAULT),
                        connection(approval, processing, ConnectionType.REJECT)));

        assertThat(hasCycle).isTrue();
    }

    @Test
    void stillRejectsOrdinaryAutomaticCycle() {
        WorkflowStep first = step(StepType.ASSIGNMENT, "{}");
        WorkflowStep second = step(StepType.ASSIGNMENT, "{}");

        boolean hasCycle = validation.hasCycle(
                List.of(first, second),
                List.of(
                        connection(first, second, ConnectionType.DEFAULT),
                        connection(second, first, ConnectionType.DEFAULT)));

        assertThat(hasCycle).isTrue();
    }

    private WorkflowStep step(StepType type, String config) {
        return WorkflowStep.builder().id(UUID.randomUUID()).type(type).configJson(config).build();
    }

    private WorkflowConnection connection(WorkflowStep from, WorkflowStep to, ConnectionType type) {
        return WorkflowConnection.builder().id(UUID.randomUUID()).fromStep(from).toStep(to).type(type).build();
    }
}
