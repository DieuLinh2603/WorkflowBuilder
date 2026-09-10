package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.runtime.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface WorkflowTaskRepository extends JpaRepository<WorkflowTask, UUID> {
    List<WorkflowTask> findByAssigneeIdAndStatusOrderByCreatedAtDesc(UUID userId, TaskStatus status);
    List<WorkflowTask> findByAssigneeIdOrderByCreatedAtDesc(UUID userId);
    boolean existsByInstanceIdAndAssigneeId(UUID instanceId, UUID assigneeId);
    List<WorkflowTask> findByInstanceIdAndAssigneeId(UUID instanceId, UUID assigneeId);

    Optional<WorkflowTask> findByInstanceIdAndStepIdAndAssigneeIdAndStatus(UUID instanceId, UUID stepId,
            UUID assigneeId, TaskStatus status);

    List<WorkflowTask> findByStatusAndDeadlineAtBetween(TaskStatus status, LocalDateTime from, LocalDateTime to);

    List<WorkflowTask> findByInstanceIdAndStatus(UUID instanceId, TaskStatus status);

    List<WorkflowTask> findByInstanceIdAndStepIdAndActivationId(UUID instanceId, UUID stepId, UUID activationId);
}
