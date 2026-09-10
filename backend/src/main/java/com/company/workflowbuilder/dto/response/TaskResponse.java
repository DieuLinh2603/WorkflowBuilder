package com.company.workflowbuilder.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
public class TaskResponse {
    private UUID id;
    private UUID instanceId;
    private String instanceStatus;
    private UUID instanceCurrentStepId;
    private String requestCode;
    private String workflowName;
    private String requesterName;
    private UUID stepId;
    private String stepLabel;
    private String stepType;
    private String status;
    private Map<String, Object> fields;
    private List<com.company.workflowbuilder.dto.ReviewResultItem> reviewResults;
    private List<com.company.workflowbuilder.dto.CalculatedOutput> calculatedOutputs;
    private List<Map<String, Object>> calculatedRows;
    private List<CustomFieldResponse> fieldDefinitions;
    private boolean batch;
    private List<Map<String, Object>> batchRecords;
    private boolean commentRequired;
    private boolean fieldsEditable;
    private String resultMode;
    private boolean canFail;
    private LocalDateTime deadlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
