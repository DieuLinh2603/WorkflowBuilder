package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.workflow.Workflow;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WorkflowRepository extends JpaRepository<Workflow, UUID> {

    Page<Workflow> findByStatus(WorkflowStatus status, Pageable pageable);

    List<Workflow> findByOwnerId(UUID ownerId);

    Page<Workflow> findByStatusNot(WorkflowStatus status, Pageable pageable);

    List<Workflow> findByFamilyIdAndStatus(UUID familyId, WorkflowStatus status);

    List<Workflow> findByFamilyId(UUID familyId);

    boolean existsByModuleAndStatus(String module, WorkflowStatus status);

    boolean existsByOwnerIdAndModuleAndStatusNotIn(UUID ownerId, String module, java.util.Collection<WorkflowStatus> statuses);

    boolean existsByModuleAndVersionAndNameIgnoreCase(String module, String version, String name);

    boolean existsByModuleAndVersionAndNameIgnoreCaseAndFamilyIdNot(
            String module, String version, String name, UUID familyId);
}
