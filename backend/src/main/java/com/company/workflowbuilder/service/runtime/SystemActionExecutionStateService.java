package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.repository.SystemActionExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemActionExecutionStateService {
    private final SystemActionExecutionRepository executions;

    @Transactional(readOnly = true)
    public List<UUID> due() {
        return executions.findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                List.of(SystemActionExecutionStatus.QUEUED, SystemActionExecutionStatus.RETRY_WAIT),
                LocalDateTime.now()).stream().map(SystemActionExecution::getId).toList();
    }

    @Transactional
    public Optional<SystemActionExecution> claim(UUID id) {
        return executions.findById(id).filter(value -> (value.getStatus() == SystemActionExecutionStatus.QUEUED
                || value.getStatus() == SystemActionExecutionStatus.RETRY_WAIT)
                && !value.getNextAttemptAt().isAfter(LocalDateTime.now())).map(value -> {
                    value.setStatus(SystemActionExecutionStatus.RUNNING);
                    value.setAttemptCount(value.getAttemptCount() + 1);
                    value.setStartedAt(LocalDateTime.now());
                    value.setErrorMessage(null);
                    return executions.save(value);
                });
    }

    @Transactional
    public void retry(UUID id, RestConnectorHttpClient.Result result) {
        SystemActionExecution value = executions.findById(id).orElseThrow();
        if (value.getStatus() != SystemActionExecutionStatus.RUNNING) return;
        value.setResponseStatus(result.statusCode());
        value.setResponseBody(auditBody(result.body()));
        value.setErrorMessage(limit(result.error(), 2000));
        value.setStatus(SystemActionExecutionStatus.RETRY_WAIT);
        long delay = Math.min(300, 5L << Math.min(20, Math.max(0, value.getAttemptCount() - 1)));
        value.setNextAttemptAt(LocalDateTime.now().plusSeconds(delay));
        executions.save(value);
    }

    @Transactional
    public void recoverStale() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        for (SystemActionExecution value : executions.findByStatusAndStartedAtLessThan(
                SystemActionExecutionStatus.RUNNING, cutoff)) {
            value.setStatus(SystemActionExecutionStatus.RETRY_WAIT);
            value.setErrorMessage("Worker stopped before recording the connector result");
            value.setNextAttemptAt(LocalDateTime.now());
            executions.save(value);
        }
    }

    public static String auditBody(String body) { return limit(body, 64 * 1024); }
    public static String limit(String value, int max) {
        if (value == null || value.length() <= max) return value;
        return value.substring(0, max) + "\n...[truncated]";
    }
}
