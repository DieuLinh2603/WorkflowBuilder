package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.*;

@Data
public class ReviewConfigRequest {
    public enum ResultMode {
        COMMENT_ONLY, REQUIRE_APPROVAL
    }

    @NotNull
    private ApprovalConfigRequest.ApproverMode approverMode;
    private List<UUID> actorUserIds = new ArrayList<>();
    private String fixedUserEmail;
    private String actorRole;
    private ApprovalConfigRequest.DynamicActorSource dynamicActorSource;
    private String reviewContent = "REQUEST_CONTENT";
    private boolean commentRequired;
    private ResultMode resultMode = ResultMode.REQUIRE_APPROVAL;
    private Integer deadlineHours;
    private LocalDate deadlineDate;
    private AssignmentConfigRequest.CompletionMode completionMode = AssignmentConfigRequest.CompletionMode.ANY;
    private Integer completionPercentage = 50;
}
