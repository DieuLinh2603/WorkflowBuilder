package com.company.workflowbuilder.dto.request;

import com.company.workflowbuilder.entity.workflow.StepType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StepCreateRequest {

    @NotNull(message = "Step type is required")
    private StepType type;

    private String label;

    private Integer positionX;

    private Integer positionY;
}
