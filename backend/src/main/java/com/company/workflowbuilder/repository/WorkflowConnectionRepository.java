package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.workflow.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowConnectionRepository extends JpaRepository<WorkflowConnection, UUID> {
    List<WorkflowConnection> findByWorkflowId(UUID workflowId);

    @Query("select c from WorkflowConnection c where c.fromStep.id=:stepId order by c.priority asc, c.id asc")
    List<WorkflowConnection> findByFromStepId(@Param("stepId") UUID stepId);

    boolean existsByFromStepIdAndType(UUID stepId, ConnectionType type);

    void deleteByFromStepIdOrToStepId(UUID fromStepId, UUID toStepId);
}
