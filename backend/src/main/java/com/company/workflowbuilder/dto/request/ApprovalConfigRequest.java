package com.company.workflowbuilder.dto.request;

import com.company.workflowbuilder.entity.workflow.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.*;
import java.time.LocalDate;

@Data
public class ApprovalConfigRequest {
    public enum Mode {
        MANUAL, AUTO
    }

    public enum ApproverMode {
        FIXED_USER, ROLE_BASED, DYNAMIC
    }

    public enum DynamicActorSource {
        REQUEST_CREATOR, REQUEST_CREATOR_MANAGER, PREVIOUS_ACTOR_MANAGER
    }

    public enum EscalationAction {
        REMIND, ESCALATE_MANAGER, AUTO_REJECT
    }

    @NotNull
    private Mode mode;
    private ApproverMode approverMode = ApproverMode.FIXED_USER;
    private List<UUID> actorUserIds = new ArrayList<>();
    private String fixedUserEmail;
    private String actorRole;
    private DynamicActorSource dynamicActorSource;
    private boolean requesterDepartmentScope;
    private LogicalOperator logicalOperator = LogicalOperator.AND;
    @Valid
    private List<ConnectionUpsertRequest.Clause> autoConditions = new ArrayList<>();
    private Integer deadlineHours;
    private LocalDate deadlineDate;
    private Set<String> reminderChannels = new HashSet<>();
    private EscalationAction escalationAction = EscalationAction.REMIND;
    private AssignmentConfigRequest.CompletionMode completionMode = AssignmentConfigRequest.CompletionMode.ANY;
    private Integer completionPercentage = 50;
}
