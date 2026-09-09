package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.WorkflowDataBinding;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface WorkflowDataBindingRepository extends JpaRepository<WorkflowDataBinding, UUID> {
    List<WorkflowDataBinding> findByDatasetIdAndActiveTrue(UUID datasetId);
    List<WorkflowDataBinding> findByWorkflowId(UUID workflowId);
    Optional<WorkflowDataBinding> findByDatasetIdAndWorkflowId(UUID datasetId, UUID workflowId);
}
