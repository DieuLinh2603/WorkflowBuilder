package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class StepUpdateRequest {
    @NotBlank(message = "Step label is required")
    @Size(max = 120, message = "Step label must not exceed 120 characters")
    private String label;
}
