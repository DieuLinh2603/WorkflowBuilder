package com.company.workflowbuilder.service.workflow;

import com.company.workflowbuilder.dto.response.ConnectionResponse;
import com.company.workflowbuilder.dto.response.WorkflowResponse;
import com.company.workflowbuilder.dto.response.WorkflowEditorResponse;
import com.company.workflowbuilder.dto.response.WorkflowStepResponse;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowConnection;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import com.company.workflowbuilder.service.CurrentUserService;
import com.company.workflowbuilder.service.WorkflowAuthorizationService;
import com.company.workflowbuilder.service.WorkflowMetadataService;
import com.company.workflowbuilder.dto.response.WorkflowTypeDefinitionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
@RequiredArgsConstructor(onConstructor_ = @org.springframework.beans.factory.annotation.Autowired)
public class WorkflowViewMapper {
    private final WorkflowAuthorizationService authorization;
    private final CurrentUserService currentUser;
    private final WorkflowMetadataService metadata;

    /** Kept for unit tests and integrations compiled against the pre-metadata mapper. */
    public WorkflowViewMapper(WorkflowAuthorizationService authorization, CurrentUserService currentUser) {
        this(authorization, currentUser, null);
    }

    public WorkflowResponse workflow(Workflow value) {
        User owner = value.getOwner();
        var module = metadata == null ? null : metadata.moduleByCode(value.getModule());
        WorkflowTypeDefinitionResponse type = metadata == null ? null : metadata.typeByCode(value.getType());
        return WorkflowResponse.builder()
                .id(value.getId()).name(value.getName()).description(value.getDescription())
                .type(value.getType()).typeName(value.getCustomTypeName() == null || value.getCustomTypeName().isBlank()
                        ? (type == null ? value.getType() : type.getName()) : value.getCustomTypeName())
                .module(value.getModule()).moduleName(module == null ? value.getModule() : module.getName())
                .recommendedStepTypes(type == null ? java.util.List.of() : type.getRecommendedStepTypes())
                .typeChecklist(type == null ? java.util.List.of() : type.getChecklist())
                .ownerId(owner.getId()).ownerName(owner.getDisplayName())
                .ownerAvatarInitials(initials(owner.getDisplayName()))
                .editors(value.getEditors().stream()
                        .sorted(Comparator.comparing(User::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .map(editor -> WorkflowEditorResponse.builder()
                                .id(editor.getId()).displayName(editor.getDisplayName())
                                .email(editor.getEmail()).jobTitle(editor.getJobTitle()).build())
                        .toList())
                .version(value.getVersion()).status(value.getStatus().name())
                .createdAt(value.getCreatedAt()).familyId(value.getFamilyId())
                .canEdit(authorization.canEdit(value)).canPublish(authorization.canPublish(value))
                .canManageEditors(value.getStatus() == WorkflowStatus.DRAFT && authorization.canPublish(value))
                .canDuplicate(currentUser.hasRole(SystemRole.ADMIN) || currentUser.hasRole(SystemRole.WORKFLOW_OWNER))
                .build();
    }

    public WorkflowStepResponse step(WorkflowStep value) {
        return WorkflowStepResponse.builder().id(value.getId()).type(value.getType()).label(value.getLabel())
                .positionX(value.getPositionX()).positionY(value.getPositionY()).build();
    }

    public ConnectionResponse connection(WorkflowConnection value) {
        return ConnectionResponse.builder().id(value.getId())
                .fromStepId(value.getFromStep().getId()).toStepId(value.getToStep().getId())
                .type(value.getType()).logicalOperator(value.getLogicalOperator())
                .clauses(value.getClauses().stream().map(clause -> ConnectionResponse.ClauseResponse.builder()
                        .id(clause.getId()).fieldKey(clause.getFieldKey()).operator(clause.getOperator())
                        .expectedValue(clause.getExpectedValue()).build()).toList())
                .build();
    }

    private String initials(String name) {
        if (name == null || name.isBlank())
            return "NA";
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 1)
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        return (parts[0].substring(0, 1) + parts[parts.length - 1].substring(0, 1)).toUpperCase();
    }
}
