package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.PipelineRun;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface PipelineRunRepository extends JpaRepository<PipelineRun, UUID> {
    List<PipelineRun> findByPipelineIdOrderByCreatedAtDesc(UUID pipelineId);
    boolean existsByPipelineIdAndScheduledFor(UUID pipelineId, LocalDateTime scheduledFor);
    List<PipelineRun> findByStatusInAndNextAttemptAtLessThanEqual(Collection<String> statuses, LocalDateTime now);
}
