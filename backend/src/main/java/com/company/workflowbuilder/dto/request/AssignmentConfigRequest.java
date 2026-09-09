package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;
import java.util.*;

@Data
public class AssignmentConfigRequest {
    public enum TargetType { USER, GROUP }
    public enum CompletionMode { ANY, ALL, PERCENTAGE }

    @NotNull private TargetType assignmentTargetType;
    private List<UUID> actorUserIds = new ArrayList<>();
    private List<UUID> assignmentGroupIds = new ArrayList<>();
    private CompletionMode completionMode = CompletionMode.ANY;
    private Integer completionPercentage = 50;
    private Integer deadlineHours;
    private LocalDate deadlineDate;
}
