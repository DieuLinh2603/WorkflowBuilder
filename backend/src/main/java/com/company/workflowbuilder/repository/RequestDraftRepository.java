package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.runtime.RequestDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RequestDraftRepository extends JpaRepository<RequestDraft, UUID> {
    Optional<RequestDraft> findByUserIdAndWorkflowFamilyId(UUID userId, UUID workflowFamilyId);

    long deleteByUserIdAndWorkflowFamilyId(UUID userId, UUID workflowFamilyId);
}
