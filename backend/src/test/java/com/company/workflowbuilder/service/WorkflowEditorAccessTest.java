package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.request.WorkflowMetadataUpdateRequest;
import com.company.workflowbuilder.dto.response.WorkflowResponse;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.exception.DuplicateResourceException;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.workflow.WorkflowDefinitionAnalysis;
import com.company.workflowbuilder.service.workflow.WorkflowQueryUseCase;
import com.company.workflowbuilder.service.workflow.WorkflowViewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkflowEditorAccessTest {
    private WorkflowRepository workflows;
    private UserRepository users;
    private WorkflowAuthorizationService authorization;
    private WorkflowViewMapper viewMapper;
    private WorkflowService service;

    @BeforeEach
    void setUp() {
        workflows = mock(WorkflowRepository.class);
        users = mock(UserRepository.class);
        authorization = mock(WorkflowAuthorizationService.class);
        viewMapper = mock(WorkflowViewMapper.class);
        WorkflowStepRepository steps = mock(WorkflowStepRepository.class);
        CustomFieldDefinitionRepository fields = mock(CustomFieldDefinitionRepository.class);
        WorkflowConnectionRepository connections = mock(WorkflowConnectionRepository.class);
        when(workflows.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(viewMapper.workflow(any())).thenReturn(WorkflowResponse.builder().build());
        service = new WorkflowService(workflows, steps, users, connections, fields,
                mock(CurrentUserService.class), authorization, mock(WorkflowValidationService.class),
                mock(WorkflowAudienceRepository.class), mock(WorkflowInstanceRepository.class), viewMapper,
                new WorkflowDefinitionAnalysis(steps, fields, connections), mock(WorkflowQueryUseCase.class));
    }

    @Test
    void ownerCanAssignAnActiveEditorToWorkflow() {
        User owner = user("owner@company.com", SystemRole.WORKFLOW_OWNER);
        User editor = user("editor@company.com", SystemRole.EDITOR);
        editor.setManager(owner);
        Workflow workflow = workflow(owner, WorkflowStatus.DRAFT);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(users.findById(editor.getId())).thenReturn(Optional.of(editor));

        service.addEditor(workflow.getId(), editor.getId());

        assertTrue(workflow.getEditors().contains(editor));
        verify(authorization).requireOwnerOrAdmin(workflow);
        verify(workflows).save(workflow);
    }

    @Test
    void ownerCanAssignAnActiveViewerAsWorkflowScopedEditor() {
        User owner = user("owner@company.com", SystemRole.WORKFLOW_OWNER);
        User viewer = user("viewer@company.com", SystemRole.VIEWER);
        viewer.setManager(owner);
        Workflow workflow = workflow(owner, WorkflowStatus.DRAFT);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(users.findById(viewer.getId())).thenReturn(Optional.of(viewer));

        service.addEditor(workflow.getId(), viewer.getId());

        assertTrue(workflow.getEditors().contains(viewer));
        verify(workflows).save(workflow);
    }

    @Test
    void onlyActiveEditorOrViewerAccountsCanBeAssigned() {
        User owner = user("owner@company.com", SystemRole.WORKFLOW_OWNER);
        Workflow workflow = workflow(owner, WorkflowStatus.DRAFT);
        User admin = user("admin@company.com", SystemRole.ADMIN);
        User inactiveViewer = user("inactive@company.com", SystemRole.VIEWER);
        inactiveViewer.setActive(false);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(users.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(users.findById(inactiveViewer.getId())).thenReturn(Optional.of(inactiveViewer));

        assertThrows(IllegalArgumentException.class, () -> service.addEditor(workflow.getId(), admin.getId()));
        assertThrows(IllegalArgumentException.class, () -> service.addEditor(workflow.getId(), inactiveViewer.getId()));
        verify(workflows, never()).save(any());
    }

    @Test
    void ownerCanRemoveAnAssignedEditor() {
        User owner = user("owner@company.com", SystemRole.WORKFLOW_OWNER);
        User editor = user("editor@company.com", SystemRole.EDITOR);
        Workflow workflow = workflow(owner, WorkflowStatus.DRAFT);
        workflow.getEditors().add(editor);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));

        service.removeEditor(workflow.getId(), editor.getId());

        assertTrue(workflow.getEditors().isEmpty());
        verify(authorization).requireOwnerOrAdmin(workflow);
        verify(workflows).save(workflow);
    }

    @Test
    void removingAnUnassignedEditorReturnsNotFound() {
        Workflow workflow = workflow(user("owner@company.com", SystemRole.WORKFLOW_OWNER), WorkflowStatus.DRAFT);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));

        assertThrows(ResourceNotFoundException.class,
                () -> service.removeEditor(workflow.getId(), UUID.randomUUID()));
        verify(workflows, never()).save(any());
    }

    @Test
    void editorAssignmentsCannotChangeOnPublishedVersion() {
        Workflow workflow = workflow(user("owner@company.com", SystemRole.WORKFLOW_OWNER), WorkflowStatus.PUBLISHED);
        User editor = user("editor@company.com", SystemRole.EDITOR);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(users.findById(editor.getId())).thenReturn(Optional.of(editor));

        assertThrows(IllegalStateException.class, () -> service.addEditor(workflow.getId(), editor.getId()));
        assertThrows(IllegalStateException.class, () -> service.removeEditor(workflow.getId(), editor.getId()));
        verify(workflows, never()).save(any());
    }

    @Test
    void rejectsEditorOutsideOwnerManagement() {
        User owner = user("owner@company.com", SystemRole.WORKFLOW_OWNER);
        User editor = user("editor@company.com", SystemRole.EDITOR);
        Workflow workflow = workflow(owner, WorkflowStatus.DRAFT);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(users.findById(editor.getId())).thenReturn(Optional.of(editor));
        assertThrows(IllegalArgumentException.class, () -> service.addEditor(workflow.getId(), editor.getId()));
        editor.setManager(user("another-owner@company.com", SystemRole.WORKFLOW_OWNER));
        assertThrows(IllegalArgumentException.class, () -> service.addEditor(workflow.getId(), editor.getId()));
        verify(workflows, never()).save(any());
    }

    @Test
    void candidatesAreScopedToOwnerAndWorkflowModule() {
        User owner = user("owner@company.com", SystemRole.WORKFLOW_OWNER);
        Workflow workflow = workflow(owner, WorkflowStatus.DRAFT);
        workflow.setModule("HR");
        User eligible = user("eligible@company.com", SystemRole.VIEWER);
        eligible.getModuleCodes().add("HR");
        User wrongModule = user("other@company.com", SystemRole.EDITOR);
        User assigned = user("assigned@company.com", SystemRole.EDITOR);
        assigned.getModuleCodes().add("HR");
        workflow.getEditors().add(assigned);
        User wrongRole = user("owner2@company.com", SystemRole.WORKFLOW_OWNER);
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(users.findByActiveTrueAndManager_Id(owner.getId()))
                .thenReturn(java.util.List.of(eligible, wrongModule, assigned, wrongRole));
        assertEquals(java.util.List.of(eligible.getId()), service.editorCandidates(workflow.getId()).stream()
                .map(com.company.workflowbuilder.dto.response.WorkflowEditorResponse::getId).toList());
        verify(authorization).requireOwnerOrAdmin(workflow);
    }

    @Test
    void scopedEditorCanUpdateDraftNameAndDescription() {
        Workflow workflow = workflow(user("owner@company.com", SystemRole.WORKFLOW_OWNER), WorkflowStatus.DRAFT);
        workflow.setModule("HR");
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        WorkflowMetadataUpdateRequest request = new WorkflowMetadataUpdateRequest();
        request.setName("  Onboarding nhân viên  ");
        request.setDescription("  Quy trình tiếp nhận nhân sự mới  ");

        service.updateMetadata(workflow.getId(), request);

        assertEquals("Onboarding nhân viên", workflow.getName());
        assertEquals("Quy trình tiếp nhận nhân sự mới", workflow.getDescription());
        verify(authorization).requireEdit(workflow);
        verify(workflows).save(workflow);
    }

    @Test
    void updateMetadataRejectsNameUsedByAnotherWorkflowFamily() {
        Workflow workflow = workflow(user("owner@company.com", SystemRole.WORKFLOW_OWNER), WorkflowStatus.DRAFT);
        workflow.setModule("HR");
        when(workflows.findById(workflow.getId())).thenReturn(Optional.of(workflow));
        when(workflows.existsByModuleAndVersionAndNameIgnoreCaseAndFamilyIdNot(
                "HR", "1.0", "Onboarding", workflow.getFamilyId())).thenReturn(true);
        WorkflowMetadataUpdateRequest request = new WorkflowMetadataUpdateRequest();
        request.setName("Onboarding");

        assertThrows(DuplicateResourceException.class,
                () -> service.updateMetadata(workflow.getId(), request));
        verify(workflows, never()).save(any());
    }

    private User user(String email, SystemRole role) {
        return User.builder().id(UUID.randomUUID()).email(email).displayName(email)
                .passwordHash("hash").active(true).systemRoles(new HashSet<>(Set.of(role))).build();
    }

    private Workflow workflow(User owner, WorkflowStatus status) {
        return Workflow.builder().id(UUID.randomUUID()).name("Workflow").owner(owner)
                .familyId(UUID.randomUUID()).status(status).editors(new HashSet<>()).build();
    }
}
