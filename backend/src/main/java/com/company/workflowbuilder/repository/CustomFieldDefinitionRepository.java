package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.field.CustomFieldDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomFieldDefinitionRepository extends JpaRepository<CustomFieldDefinition, UUID> {

    List<CustomFieldDefinition> findByStepIdOrderByDisplayOrderAsc(UUID stepId);

    boolean existsByStepIdAndFieldKey(UUID stepId, String fieldKey);

    List<CustomFieldDefinition> findByStepWorkflowId(UUID workflowId);
}
