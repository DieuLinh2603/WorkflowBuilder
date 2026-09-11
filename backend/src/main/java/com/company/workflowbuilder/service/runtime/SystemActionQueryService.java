package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.runtime.WorkflowInstance;
import com.company.workflowbuilder.entity.user.SystemRole;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemActionQueryService {
    private final SystemActionExecutionRepository executions;
    private final WorkflowInstanceRepository instances;
    private final WorkflowTaskRepository tasks;
    private final WorkflowBatchRecordRepository batchRecords;
    private final WorkflowBatchRecordAccessRepository batchRecordAccess;
    private final CurrentUserService currentUser;
    private final WorkflowJsonCodec codec;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> forInstance(UUID instanceId) {
        WorkflowInstance instance = instances.findById(instanceId)
                .orElseThrow(() -> new ResourceNotFoundException("WorkflowInstance", "id", instanceId));
        boolean privileged = currentUser.hasRole(SystemRole.ADMIN)
                || instance.getWorkflow().getOwner().getId().equals(currentUser.id());
        boolean allowed = privileged
                || instance.getCreatedBy().getId().equals(currentUser.id())
                || tasks.existsByInstanceIdAndAssigneeId(instanceId, currentUser.id())
                || !batchRecordAccess.findAccessibleRecordIds(instanceId, currentUser.id()).isEmpty();
        if (!allowed) throw new AccessDeniedException("Cannot view System Action executions for this request");
        boolean fullInstance = privileged || instance.getCreatedBy().getId().equals(currentUser.id());
        Set<UUID> accessibleRows = fullInstance ? Set.of()
                : batchRecordAccess.findAccessibleRecordIds(instanceId, currentUser.id());
        return executions.findByInstanceIdOrderByCreatedAtAsc(instanceId).stream()
                .filter(value -> fullInstance || value.getBatchRowNumber() == null
                        || batchRecords.findFirstByInstanceIdAndRowNumberOrderByRevisionDesc(
                                instanceId, value.getBatchRowNumber())
                                .map(row -> accessibleRows.contains(row.getId())).orElse(false))
                .map(value -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", value.getId()); row.put("stepId", value.getStep().getId());
            row.put("stepLabel", value.getStep().getLabel()); row.put("batchRowNumber", value.getBatchRowNumber());
            row.put("connectorId", value.getConnector() == null ? null : value.getConnector().getId());
            row.put("connectorName", value.getConnector() == null ? null : value.getConnector().getName());
            row.put("status", value.getStatus()); row.put("attemptCount", value.getAttemptCount());
            row.put("maxAttempts", value.getMaxAttempts()); row.put("httpMethod", value.getHttpMethod());
            row.put("responseStatus", value.getResponseStatus());
            if (privileged) {
                row.put("requestUrl", value.getRequestUrl());
                row.put("responseBody", value.getResponseBody());
                row.put("mappedOutputs", codec.snapshot(value.getMappedOutputsJson()));
                row.put("errorMessage", value.getErrorMessage());
            } else if (value.getErrorMessage() != null) {
                row.put("errorMessage", "System Action thất bại (execution " + value.getId() + ")");
            }
            row.put("nextAttemptAt", value.getNextAttemptAt());
            row.put("startedAt", value.getStartedAt()); row.put("completedAt", value.getCompletedAt());
            row.put("resumedAt", value.getResumedAt());
            row.put("createdAt", value.getCreatedAt());
            return row;
        }).toList();
    }
}
