package com.company.workflowbuilder.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
public class TaskResponse {
    private UUID id;
    private UUID instanceId;
    private String requestCode;
    private String workflowName;
    private String requesterName;
    private UUID stepId;
    private String stepLabel;
    private String stepType;
    private String status;
    private Map<String, Object> fields;
    private boolean batch;
    private List<Map<String, Object>> batchRecords;
    private boolean commentRequired;
    private boolean fieldsEditable;
    private String resultMode;
    private LocalDateTime deadlineAt;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
