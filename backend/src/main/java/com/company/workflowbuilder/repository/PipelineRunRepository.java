package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.PipelineRun;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {
    @Override
    @EntityGraph(attributePaths = {"pipeline", "pipeline.owner"})
    Optional<PipelineRun> findById(UUID id);

    @EntityGraph(attributePaths = {"pipeline", "pipeline.owner"})
    List<PipelineRun> findByPipelineIdOrderByCreatedAtDesc(UUID pipelineId);
    @EntityGraph(attributePaths = {"pipeline", "pipeline.owner"})
    Optional<PipelineRun> findFirstByPipelineIdAndStatusInOrderByCreatedAtDesc(UUID pipelineId, Collection<String> statuses);
    boolean existsByPipelineIdAndScheduledFor(UUID pipelineId, LocalDateTime scheduledFor);
    @EntityGraph(attributePaths = {"pipeline", "pipeline.owner"})
    List<PipelineRun> findByStatusInAndNextAttemptAtLessThanEqual(Collection<String> statuses, LocalDateTime now);
    long countByPipelineId(UUID pipelineId);
    boolean existsByPipelineIdAndStatusIn(UUID pipelineId, Collection<String> statuses);
}
