package com.company.workflowbuilder.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class BatchInstanceResponse {
    private UUID batchId;
    private int total;
    private List<InstanceResponse> instances;
}
