package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.util.*;

@Data
public class CreateInstanceRequest {
    @NotNull
    private UUID workflowId;
    private Map<String, Object> fields = new HashMap<>();
}
