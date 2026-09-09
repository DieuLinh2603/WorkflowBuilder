package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.workflow.StepType;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowStepRepository extends JpaRepository<WorkflowStep, UUID> {

    List<WorkflowStep> findByWorkflowIdOrderByPositionXAsc(UUID workflowId);

    boolean existsByWorkflowIdAndType(UUID workflowId, StepType type);

    long countByWorkflowIdAndType(UUID workflowId, StepType type);
}
