package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkflowTypeDefinitionRequest {
    @NotBlank(message = "Workflow type code is required")
    @Pattern(regexp = "[A-Z][A-Z0-9_]{1,63}", message = "Workflow type code must use uppercase letters, numbers and underscores")
    private String code;

    @NotBlank(message = "Workflow type name is required")
    private String name;

    private String description;

    private List<String> recommendedStepTypes = new ArrayList<>();
    private List<String> checklist = new ArrayList<>();

    @Min(value = 0, message = "Sort order cannot be negative")
    private int sortOrder;
}
