package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.runtime.InstanceStepLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InstanceStepLogRepository extends JpaRepository<InstanceStepLog, UUID> {
    List<InstanceStepLog> findByInstanceIdOrderByActedAtAsc(UUID id);
}
