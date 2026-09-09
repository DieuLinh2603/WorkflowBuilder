package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.runtime.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, UUID> {
    List<WorkflowInstance> findByCreatedByIdOrderByStartedAtDesc(UUID userId);

    List<WorkflowInstance> findAllByOrderByStartedAtDesc();

    boolean existsByWorkflowId(UUID workflowId);
}
