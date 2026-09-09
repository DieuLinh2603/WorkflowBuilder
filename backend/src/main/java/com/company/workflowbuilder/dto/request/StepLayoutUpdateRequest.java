package com.company.workflowbuilder.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class StepLayoutUpdateRequest {
    @NotEmpty
    @Valid
    private List<Position> positions = new ArrayList<>();

    @Data
    public static class Position {
        @NotNull private UUID stepId;
        @NotNull private Integer positionX;
        @NotNull private Integer positionY;
    }
}
