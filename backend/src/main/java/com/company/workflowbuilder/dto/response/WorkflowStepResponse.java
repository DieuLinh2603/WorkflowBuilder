package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.workflow.StepType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkflowStepResponse {
    private UUID id;
    private StepType type;
    private String label;
    private int positionX;
    private int positionY;
}
