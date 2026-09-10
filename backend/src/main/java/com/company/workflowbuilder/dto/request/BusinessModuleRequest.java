package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BusinessModuleRequest {
    @NotBlank(message = "Module code is required")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "Module code must use uppercase letters, numbers and underscores")
    private String code;

    @NotBlank(message = "Module name is required")
    private String name;

    private String description;

    @Min(value = 0, message = "Sort order cannot be negative")
    private int sortOrder;
}
