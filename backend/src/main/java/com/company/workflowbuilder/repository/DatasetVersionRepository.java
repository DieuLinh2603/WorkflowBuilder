package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.DatasetVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DatasetVersionRepository extends JpaRepository<DatasetVersion, UUID> {
    Optional<DatasetVersion> findByDatasetIdAndVersionNumber(UUID datasetId, int versionNumber);
    List<DatasetVersion> findByDatasetIdOrderByVersionNumberDesc(UUID datasetId);
}
