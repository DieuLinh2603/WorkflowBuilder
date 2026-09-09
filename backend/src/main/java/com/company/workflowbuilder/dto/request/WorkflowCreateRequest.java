package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowCreateRequest {

    @NotBlank(message = "Tên Workflow là bắt buộc")
    private String name;

    private String description;

    private String workflowType;

    private String module;

    /** Admin-only override. Backend ignores this field for non-admin users. */
    private UUID ownerId;
}
