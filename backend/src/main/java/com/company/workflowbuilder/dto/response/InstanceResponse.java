package com.company.workflowbuilder.dto.response;

import com.company.workflowbuilder.entity.runtime.InstanceStatus;
import lombok.*;
import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
public class InstanceResponse {
    private UUID id;
    private String requestCode;
    private UUID batchId;
    private Integer batchRowNumber;
    private boolean batch;
    private Integer batchTotal;
    private Map<String, Long> batchStatusCounts;
    private UUID workflowId;
    private String workflowName;
    private String workflowVersion;
    private UUID createdById;
    private String createdByName;
    private UUID currentStepId;
    private String currentStepLabel;
    private String currentStepType;
    private InstanceStatus status;
    private boolean conditionBlocked;
    private Map<String, Object> fields;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private UUID withdrawnById;
    private String withdrawnByName;
    private LocalDateTime withdrawnAt;
    private String withdrawalReason;
    private boolean requesterWithdrawalAllowed;
}
