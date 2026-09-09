package com.company.workflowbuilder.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class StepDeleteResponse {
    private UUID deletedStepId;
    private List<UUID> deletedConnectionIds;
    private ConnectionResponse replacementConnection;
}
