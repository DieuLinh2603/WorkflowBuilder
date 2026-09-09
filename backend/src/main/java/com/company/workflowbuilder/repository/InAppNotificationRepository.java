package com.company.workflowbuilder.repository;

import com.company.workflowbuilder.entity.notification.InAppNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {
    List<InAppNotification> findTop50ByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
