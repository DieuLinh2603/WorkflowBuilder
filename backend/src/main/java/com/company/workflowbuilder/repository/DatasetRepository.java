package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.Dataset;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DatasetRepository extends JpaRepository<Dataset, UUID> {
    Optional<Dataset> findByPipelineId(UUID pipelineId);
}
