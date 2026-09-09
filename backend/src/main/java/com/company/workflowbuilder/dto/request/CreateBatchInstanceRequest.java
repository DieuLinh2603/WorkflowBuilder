package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
public class CreateBatchInstanceRequest {
    @NotNull
    private UUID workflowId;
    @NotEmpty
    private List<Map<String, Object>> records = new ArrayList<>();
}
