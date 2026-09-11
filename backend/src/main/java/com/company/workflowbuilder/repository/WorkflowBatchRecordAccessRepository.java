package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.runtime.WorkflowBatchRecordAccess;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WorkflowBatchRecordAccessRepository extends JpaRepository<WorkflowBatchRecordAccess, UUID> {
    boolean existsByBatchRecordIdAndUserIdAndAccessType(UUID batchRecordId, UUID userId, String accessType);
    boolean existsByBatchRecordIdAndUserId(UUID batchRecordId, UUID userId);

    @Query("select a.batchRecord.id from WorkflowBatchRecordAccess a where a.batchRecord.instance.id = :instanceId and a.user.id = :userId")
    Set<UUID> findAccessibleRecordIds(@Param("instanceId") UUID instanceId, @Param("userId") UUID userId);
}
