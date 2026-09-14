package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.runtime.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface SystemActionExecutionRepository extends JpaRepository<SystemActionExecution, UUID> {
    List<SystemActionExecution> findByInstanceIdOrderByCreatedAtAsc(UUID instanceId);
    List<SystemActionExecution> findByInstanceIdAndBatchRowNumberOrderByCreatedAtAsc(UUID instanceId, Integer batchRowNumber);
    List<SystemActionExecution> findTop50ByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            Collection<SystemActionExecutionStatus> statuses, LocalDateTime nextAttemptAt);
    List<SystemActionExecution> findByStatusAndStartedAtLessThan(SystemActionExecutionStatus status, LocalDateTime cutoff);
    List<SystemActionExecution> findTop50ByStatusInAndResumedAtIsNullOrderByCompletedAtAsc(
            Collection<SystemActionExecutionStatus> statuses);
}
