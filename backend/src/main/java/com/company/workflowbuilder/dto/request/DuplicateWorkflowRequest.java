package com.company.workflowbuilder.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Data
public class DuplicateWorkflowRequest {
    private UUID sourceVersionId;
    @NotBlank private String name;
    /** Optional target module; defaults to the source module. */
    private String module;
    /** Admin-only override. */
    private UUID ownerId;
    @JsonIgnore
    private boolean workingCopy;
}
