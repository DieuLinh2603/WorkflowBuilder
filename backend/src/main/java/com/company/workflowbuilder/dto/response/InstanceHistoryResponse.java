package com.company.workflowbuilder.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class InstanceHistoryResponse {
    private UUID id;
    private UUID stepId;
    private String stepLabel;
    private String stepType;
    private String action;
    private UUID actorId;
    private String actorName;
    private String comment;
    private LocalDateTime actedAt;
}
