package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.DataPipeline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;

public interface DataPipelineRepository extends JpaRepository<DataPipeline, UUID> {
    List<DataPipeline> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId);
    List<DataPipeline> findByStatusAndNextRunAtLessThanEqual(String status, LocalDateTime now);
}
