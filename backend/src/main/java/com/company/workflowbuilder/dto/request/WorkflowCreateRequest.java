package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "Loại Workflow là bắt buộc")
    private String workflowType;

    @Size(max = 255, message = "Tên loại Workflow khác không được vượt quá 255 ký tự")
    private String customWorkflowType;

    @NotBlank(message = "Module là bắt buộc")
    private String module;

    /** Admin-only override. Backend ignores this field for non-admin users. */
    private UUID ownerId;
}
