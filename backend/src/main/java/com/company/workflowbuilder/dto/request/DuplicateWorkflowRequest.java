package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;

@Data
public class DuplicateWorkflowRequest {
    private UUID sourceVersionId;
    @NotBlank private String name;
    /** Admin-only override. */
    private UUID ownerId;
}
