package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.notification.ReminderDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.*;
import java.util.UUID;

public interface ReminderDeliveryRepository extends JpaRepository<ReminderDelivery, UUID> {
    boolean existsByTaskIdAndChannelAndDeadlineAt(UUID taskId, String channel, LocalDateTime deadlineAt);

    List<ReminderDelivery> findTop100ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(Collection<String> statuses,
            LocalDateTime now);
}
