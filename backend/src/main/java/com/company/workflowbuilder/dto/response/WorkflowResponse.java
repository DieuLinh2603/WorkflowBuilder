package com.company.workflowbuilder.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowResponse {
    private UUID id;
    private String name;
    private String description;
    private String type;
    private String module;
    private UUID ownerId;
    private String ownerName;
    private String ownerAvatarInitials;
    @Builder.Default
    private List<WorkflowEditorResponse> editors = List.of();
    private String version;
    private String status;
    private LocalDateTime createdAt;
    private UUID familyId;
    private boolean canEdit;
    private boolean canPublish;
    private boolean canManageEditors;
    private boolean canDuplicate;
}
