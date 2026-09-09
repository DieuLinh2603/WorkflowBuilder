package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.user.UserGroup;
import com.company.workflowbuilder.entity.workflow.AudienceType;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowAudience;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.repository.UserGroupRepository;
import com.company.workflowbuilder.repository.WorkflowAudienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WorkflowAuthorizationServiceTest {
    private CurrentUserService currentUser;
    private WorkflowAuthorizationService authorization;
    private WorkflowAudienceRepository audiences;
    private UserGroupRepository groups;
    private User owner;
    private User editor;

    @BeforeEach
    void setUp() {
        currentUser = mock(CurrentUserService.class);
        audiences = mock(WorkflowAudienceRepository.class);
        groups = mock(UserGroupRepository.class);
        authorization = new WorkflowAuthorizationService(currentUser, audiences, groups);
        owner = user();
        editor = user();
    }

    @Test
    void ownerAndAssignedEditorCanEditDraft() {
        Workflow workflow = workflow(WorkflowStatus.DRAFT);
        workflow.getEditors().add(editor);

        when(currentUser.id()).thenReturn(owner.getId());
        assertTrue(authorization.canEdit(workflow));

        when(currentUser.id()).thenReturn(editor.getId());
        assertTrue(authorization.canEdit(workflow));
    }

    @Test
    void unassignedEditorCannotEditDraft() {
        when(currentUser.id()).thenReturn(editor.getId());
        assertFalse(authorization.canEdit(workflow(WorkflowStatus.DRAFT)));
    }

    @Test
    void publishedWorkflowIsImmutableForOwnerAndEditor() {
        Workflow workflow = workflow(WorkflowStatus.PUBLISHED);
        workflow.getEditors().add(editor);

        when(currentUser.id()).thenReturn(owner.getId());
        assertFalse(authorization.canEdit(workflow));
        when(currentUser.id()).thenReturn(editor.getId());
        assertFalse(authorization.canEdit(workflow));
    }

    @Test
    void onlyOwnerOrAdminCanPublishAndManageEditors() {
        Workflow workflow = workflow(WorkflowStatus.DRAFT);
        workflow.getEditors().add(editor);

        when(currentUser.id()).thenReturn(editor.getId());
        assertFalse(authorization.canPublish(workflow));

        when(currentUser.id()).thenReturn(owner.getId());
        assertTrue(authorization.canPublish(workflow));

        when(currentUser.id()).thenReturn(editor.getId());
        when(currentUser.hasRole(SystemRole.ADMIN)).thenReturn(true);
        assertTrue(authorization.canPublish(workflow));
    }

    @Test
    void publishedWorkflowCanBeSubmittedByConfiguredSystemRole() {
        Workflow workflow = workflow(WorkflowStatus.PUBLISHED);
        when(audiences.findByWorkflowId(workflow.getId())).thenReturn(List.of(
                WorkflowAudience.builder().workflow(workflow).subjectType(AudienceType.SYSTEM_ROLE)
                        .subjectValue("VIEWER").build()));
        when(currentUser.principal()).thenReturn(new com.company.workflowbuilder.security.CustomUserDetails(
                editor.getId(), editor.getEmail(), "hash", editor.getDisplayName(), true, 0,
                new HashSet<>(List.of(new SimpleGrantedAuthority("ROLE_VIEWER")))));
        when(currentUser.id()).thenReturn(editor.getId());

        assertTrue(authorization.canSubmit(workflow));
    }

    @Test
    void publishedWorkflowCanBeSubmittedByConfiguredGroupMember() {
        Workflow workflow = workflow(WorkflowStatus.PUBLISHED);
        UUID groupId = UUID.randomUUID();
        when(audiences.findByWorkflowId(workflow.getId())).thenReturn(List.of(
                WorkflowAudience.builder().workflow(workflow).subjectType(AudienceType.GROUP)
                        .subjectValue(groupId.toString()).build()));
        when(currentUser.id()).thenReturn(editor.getId());
        when(groups.findByMembersId(editor.getId())).thenReturn(List.of(
                UserGroup.builder().id(groupId).name("Purchasing").build()));

        assertTrue(authorization.canSubmit(workflow));
    }

    private User user() {
        return User.builder().id(UUID.randomUUID()).email(UUID.randomUUID() + "@company.com")
                .displayName("User").passwordHash("hash").build();
    }

    private Workflow workflow(WorkflowStatus status) {
        return Workflow.builder().id(UUID.randomUUID()).name("Workflow").owner(owner)
                .familyId(UUID.randomUUID()).status(status).editors(new HashSet<>()).build();
    }
}
