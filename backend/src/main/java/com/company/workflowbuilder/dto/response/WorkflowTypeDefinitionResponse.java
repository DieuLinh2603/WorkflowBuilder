package com.company.workflowbuilder.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WorkflowTypeDefinitionResponse {
    private String code;
    private String name;
    private String description;
    private List<String> recommendedStepTypes;
    private List<String> checklist;
    private boolean active;
    private int sortOrder;
}
