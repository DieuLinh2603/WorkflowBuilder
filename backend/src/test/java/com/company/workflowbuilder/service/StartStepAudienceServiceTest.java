package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.StartStepConfigRequest;
import com.company.workflowbuilder.dto.response.StartStepConfigResponse;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.AudienceType;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowAudience;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.repository.CustomFieldDefinitionRepository;
import com.company.workflowbuilder.repository.UserGroupRepository;
import com.company.workflowbuilder.repository.UserRepository;
import com.company.workflowbuilder.repository.WorkflowAudienceRepository;
import com.company.workflowbuilder.repository.WorkflowStepRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StartStepAudienceServiceTest {
    private WorkflowStepRepository steps;
    private WorkflowAudienceRepository audiences;
    private UserGroupRepository groups;
    private UserRepository users;
    private StartStepService service;
    private Workflow workflow;
    private WorkflowStep start;

    @BeforeEach
    void setUp() {
        steps = mock(WorkflowStepRepository.class);
        audiences = mock(WorkflowAudienceRepository.class);
        groups = mock(UserGroupRepository.class);
        users = mock(UserRepository.class);
        service = new StartStepService(steps, mock(CustomFieldDefinitionRepository.class), new ObjectMapper(),
                mock(WorkflowAuthorizationService.class), audiences, groups, users);
        workflow = Workflow.builder().id(UUID.randomUUID()).name("Purchase")
                .owner(User.builder().id(UUID.randomUUID()).email("owner@company.com")
                        .passwordHash("hash").displayName("Owner").build())
                .status(WorkflowStatus.DRAFT).build();
        start = WorkflowStep.builder().id(UUID.randomUUID()).workflow(workflow)
                .type(StepType.START).configJson("{}").build();
        when(steps.findById(start.getId())).thenReturn(Optional.of(start));
        when(steps.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void specificScopeIsSavedAsGroupAndSystemRoleAudience() throws Exception {
        UUID groupId = UUID.randomUUID();
        when(groups.existsById(groupId)).thenReturn(true);
        StartStepConfigRequest request = StartStepConfigRequest.builder()
                .requesterScope("SPECIFIC_GROUP_ROLE")
                .allowedGroupIds(List.of(groupId))
                .allowedRoles(List.of(SystemRole.EDITOR, SystemRole.VIEWER))
                .allowRequesterWithdrawal(true)
                .build();

        service.updateStartConfig(workflow.getId(), start.getId(), request);

        verify(audiences).deleteByWorkflowId(workflow.getId());
        ArgumentCaptor<WorkflowAudience> saved = ArgumentCaptor.forClass(WorkflowAudience.class);
        verify(audiences, times(3)).save(saved.capture());
        assertTrue(saved.getAllValues().stream().anyMatch(rule -> rule.getSubjectType() == AudienceType.GROUP
                && rule.getSubjectValue().equals(groupId.toString())));
        assertTrue(saved.getAllValues().stream().anyMatch(rule -> rule.getSubjectType() == AudienceType.SYSTEM_ROLE
                && rule.getSubjectValue().equals("EDITOR")));
        assertEquals("SPECIFIC_GROUP_ROLE", new ObjectMapper().readTree(start.getConfigJson())
                .get("requesterScope").asText());
    }

    @Test
    void allEmployeesCreatesAllActiveAudienceAndClearsSelections() {
        StartStepConfigRequest request = StartStepConfigRequest.builder()
                .requesterScope("ALL_EMPLOYEES")
                .allowedGroupIds(List.of(UUID.randomUUID()))
                .allowedRoles(List.of(SystemRole.ADMIN))
                .build();

        service.updateStartConfig(workflow.getId(), start.getId(), request);

        ArgumentCaptor<WorkflowAudience> saved = ArgumentCaptor.forClass(WorkflowAudience.class);
        verify(audiences).save(saved.capture());
        assertEquals(AudienceType.ALL_ACTIVE, saved.getValue().getSubjectType());
        assertEquals("*", saved.getValue().getSubjectValue());
    }

    @Test
    void specificScopeAcceptsAndReturnsMultipleUsers() {
        User first = User.builder().id(UUID.randomUUID()).active(true).displayName("First").build();
        User second = User.builder().id(UUID.randomUUID()).active(true).displayName("Second").build();
        when(users.findById(first.getId())).thenReturn(Optional.of(first));
        when(users.findById(second.getId())).thenReturn(Optional.of(second));
        StartStepConfigRequest request = StartStepConfigRequest.builder()
                .requesterScope("SPECIFIC_GROUP_ROLE")
                .allowedUserIds(List.of(first.getId(), second.getId()))
                .build();

        service.updateStartConfig(workflow.getId(), start.getId(), request);

        ArgumentCaptor<WorkflowAudience> saved = ArgumentCaptor.forClass(WorkflowAudience.class);
        verify(audiences, times(2)).save(saved.capture());
        assertEquals(List.of(first.getId().toString(), second.getId().toString()), saved.getAllValues().stream()
                .map(WorkflowAudience::getSubjectValue).toList());

        when(audiences.findByWorkflowId(workflow.getId())).thenReturn(saved.getAllValues());
        StartStepConfigResponse response = service.getStartConfig(workflow.getId(), start.getId());
        assertEquals(List.of(first.getId().toString(), second.getId().toString()), response.getAllowedUserIds());
    }

    @Test
    void specificScopeRequiresAtLeastOneGroupOrRole() {
        StartStepConfigRequest request = StartStepConfigRequest.builder()
                .requesterScope("SPECIFIC_GROUP_ROLE")
                .allowedGroupIds(List.of())
                .allowedRoles(List.of())
                .build();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateStartConfig(workflow.getId(), start.getId(), request));

        assertTrue(error.getMessage().contains("ít nhất một user, nhóm hoặc vai trò"));
        verify(audiences, never()).deleteByWorkflowId(any());
    }

    @Test
    void responseReadsCurrentAudienceRules() {
        UUID groupId = UUID.randomUUID();
        when(audiences.findByWorkflowId(workflow.getId())).thenReturn(List.of(
                WorkflowAudience.builder().workflow(workflow).subjectType(AudienceType.GROUP)
                        .subjectValue(groupId.toString()).build(),
                WorkflowAudience.builder().workflow(workflow).subjectType(AudienceType.SYSTEM_ROLE)
                        .subjectValue("VIEWER").build()));

        StartStepConfigResponse response = service.getStartConfig(workflow.getId(), start.getId());

        assertEquals("SPECIFIC_GROUP_ROLE", response.getRequesterScope());
        assertEquals(List.of(groupId.toString()), response.getAllowedGroupIds());
        assertEquals(List.of(SystemRole.VIEWER), response.getAllowedRoles());
    }
}
