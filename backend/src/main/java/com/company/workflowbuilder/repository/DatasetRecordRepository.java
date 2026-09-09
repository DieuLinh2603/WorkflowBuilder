package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.data.DatasetRecord;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface DatasetRecordRepository extends JpaRepository<DatasetRecord, UUID> {
    List<DatasetRecord> findByDatasetVersionId(UUID versionId);
    Page<DatasetRecord> findByDatasetVersionId(UUID versionId, Pageable pageable);
    List<DatasetRecord> findByDatasetVersionIdAndChangeTypeIn(UUID versionId, Collection<String> types);
}
