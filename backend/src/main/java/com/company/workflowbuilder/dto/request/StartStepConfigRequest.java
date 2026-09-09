package com.company.workflowbuilder.dto.request;

import com.company.workflowbuilder.entity.user.SystemRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartStepConfigRequest {
    private String instructionForCreator;
    private String requesterScope; // ALL_EMPLOYEES or SPECIFIC_GROUP_ROLE
    private List<UUID> allowedUserIds;
    private List<UUID> allowedGroupIds;
    private List<SystemRole> allowedRoles;
    @Builder.Default
    private Boolean allowRequesterWithdrawal = true;
    /** SINGLE keeps the legacy form; BATCH enables CSV template/import. */
    @Builder.Default
    private String submissionMode = "SINGLE";
    private String recordRecipientFieldKey;
    @Builder.Default
    private Integer maxBatchRows = 500;
}
