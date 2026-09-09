package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.user.SystemRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartStepConfigResponse {
    private String instructionForCreator;
    private String requesterScope;
    private List<String> allowedUserIds;
    private List<String> allowedGroupIds; // as string for simplicity on frontend if needed
    private List<SystemRole> allowedRoles;
    private List<CustomFieldResponse> fields;
    private boolean allowRequesterWithdrawal;
    private String submissionMode;
    private String recordRecipientFieldKey;
    private int maxBatchRows;
}
