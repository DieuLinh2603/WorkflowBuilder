package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.workflow.*;
import lombok.*;
import java.util.*;

@Data @Builder
public class ConnectionResponse {
    private UUID id;
    private UUID fromStepId;
    private UUID toStepId;
    private ConnectionType type;
    private LogicalOperator logicalOperator;
    private int priority;
    private List<ClauseResponse> clauses;

    @Data @Builder
    public static class ClauseResponse {
        private UUID id;
        private String fieldKey;
        private ConditionOperator operator;
        private String expectedValue;
        private Map<String, Object> expression;
    }
}
