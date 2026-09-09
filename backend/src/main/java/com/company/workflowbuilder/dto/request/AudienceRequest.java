package com.company.workflowbuilder.dto.request;

import com.company.workflowbuilder.entity.workflow.AudienceType;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AudienceRequest {
    @NotNull
    private AudienceType subjectType;
    @NotBlank
    private String subjectValue;
}
