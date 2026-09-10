package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.metadata.WorkflowTypeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowTypeDefinitionRepository extends JpaRepository<WorkflowTypeDefinition, String> {
    List<WorkflowTypeDefinition> findByActiveTrueOrderBySortOrderAscNameAsc();
    List<WorkflowTypeDefinition> findAllByOrderBySortOrderAscNameAsc();
    boolean existsByNameIgnoreCaseAndCodeNot(String name, String code);
    boolean existsByNameIgnoreCase(String name);
}
