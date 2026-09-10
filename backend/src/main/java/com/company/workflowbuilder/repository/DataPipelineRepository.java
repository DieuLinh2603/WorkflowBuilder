package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.DataPipeline;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface DataPipelineRepository extends JpaRepository<DataPipeline, UUID> {
    @Override
    @EntityGraph(attributePaths = {"owner", "sharedWith"})
    Optional<DataPipeline> findById(UUID id);

    List<DataPipeline> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
    List<DataPipeline> findDistinctByOwnerIdOrSharedWithIdOrderByUpdatedAtDesc(UUID ownerId, UUID sharedWithId);
    List<DataPipeline> findByStatusAndNextRunAtLessThanEqual(String status, LocalDateTime now);
}
