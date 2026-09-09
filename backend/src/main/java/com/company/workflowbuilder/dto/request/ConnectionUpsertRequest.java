package com.company.workflowbuilder.dto.request;

import com.company.workflowbuilder.entity.workflow.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import java.util.*;

@Data
public class ConnectionUpsertRequest {
    @NotNull private UUID fromStepId;
    @NotNull private UUID toStepId;
    @NotNull private ConnectionType type;
    @NotNull private LogicalOperator logicalOperator = LogicalOperator.AND;
    @Min(0) @Max(10000) private int priority = 100;
    @Valid private List<Clause> clauses = new ArrayList<>();

    @Data
    public static class Clause {
        private String fieldKey;
        private ConditionOperator operator;
        private String expectedValue;
        private Map<String, Object> expression;
    }
}
