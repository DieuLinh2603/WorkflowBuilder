package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.workflow.Workflow;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.entity.workflow.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class WorkflowAuthorizationService {
    private final CurrentUserService currentUser;
    private final WorkflowAudienceRepository audiences;
    private final UserGroupRepository groups;

    public boolean canView(Workflow workflow) {
        if (currentUser.hasRole(SystemRole.ADMIN)) return true;
        if (!hasModuleAccess(workflow)) return false;
        return workflow.getOwner().getId().equals(currentUser.id())
                || workflow.getEditors().stream().anyMatch(u -> u.getId().equals(currentUser.id()))
                || canSubmit(workflow);
    }

    public boolean canSubmit(Workflow workflow) {
        if (workflow.getStatus() != WorkflowStatus.PUBLISHED)
            return false;
        if (!hasModuleAccess(workflow)) return false;
        List<WorkflowAudience> rules = audiences.findByWorkflowId(workflow.getId());
        if (rules.isEmpty() || rules.stream().anyMatch(a -> a.getSubjectType() == AudienceType.ALL_ACTIVE))
            return true;
        Set<String> groupIds = new HashSet<>();
        groups.findByMembersId(currentUser.id()).forEach(g -> groupIds.add(g.getId().toString()));
        return rules.stream().anyMatch(a -> switch (a.getSubjectType()) {
            case USER -> a.getSubjectValue().equals(currentUser.id().toString());
            case GROUP -> groupIds.contains(a.getSubjectValue());
            case SYSTEM_ROLE -> currentUser.principal().getAuthorities().stream()
                    .anyMatch(x -> x.getAuthority().equals("ROLE_" + a.getSubjectValue()));
            case ALL_ACTIVE -> true;
        });
    }

    public boolean canEdit(Workflow workflow) {
        return workflow.getStatus() == com.company.workflowbuilder.entity.workflow.WorkflowStatus.DRAFT
                && (currentUser.hasRole(SystemRole.ADMIN)
                        || (hasModuleAccess(workflow) && (workflow.getOwner().getId().equals(currentUser.id())
                        || workflow.getEditors().stream().anyMatch(u -> u.getId().equals(currentUser.id())))));
    }

    public boolean canPublish(Workflow workflow) {
        return currentUser.hasRole(SystemRole.ADMIN)
                || (hasModuleAccess(workflow) && workflow.getOwner().getId().equals(currentUser.id()));
    }

    public void requireView(Workflow workflow) {
        if (!canView(workflow))
            throw new AccessDeniedException("You cannot view this workflow");
    }

    public void requireEdit(Workflow workflow) {
        if (!canEdit(workflow))
            throw new AccessDeniedException("You cannot edit this workflow draft");
    }

    public void requireOwnerOrAdmin(Workflow workflow) {
        if (!canPublish(workflow))
            throw new AccessDeniedException("Workflow owner or admin is required");
    }

    private boolean hasModuleAccess(Workflow workflow) {
        if (currentUser.hasRole(SystemRole.ADMIN)) return true;
        return currentUser.user().getModuleCodes().contains(workflow.getModule());
    }
}
