package com.company.workflowbuilder.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BusinessModuleResponse {
    private String code;
    private String name;
    private String description;
    private boolean active;
    private int sortOrder;
}
