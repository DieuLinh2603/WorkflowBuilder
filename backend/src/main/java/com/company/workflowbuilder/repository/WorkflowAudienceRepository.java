package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.workflow.WorkflowAudience;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WorkflowAudienceRepository extends JpaRepository<WorkflowAudience, UUID> {
    List<WorkflowAudience> findByWorkflowId(UUID workflowId);

    void deleteByWorkflowId(UUID workflowId);
}
