package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class WorkflowMetadataUpdateRequest {
    @NotBlank(message = "Tên Workflow là bắt buộc")
    @Size(max = 255, message = "Tên Workflow không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 2000, message = "Mô tả Workflow không được vượt quá 2000 ký tự")
    private String description;
}
