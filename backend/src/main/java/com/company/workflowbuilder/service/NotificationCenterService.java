package com.company.workflowbuilder.service;

import com.company.workflowbuilder.dto.response.NotificationResponse;
import com.company.workflowbuilder.entity.notification.InAppNotification;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.exception.ResourceNotFoundException;
import com.company.workflowbuilder.repository.InAppNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class NotificationCenterService {

    private final InAppNotificationRepository notifications;
    private final CurrentUserService currentUser;

    @Transactional
    public void create(User recipient, String title, String body, String requestCode, String link) {
        notifications.save(InAppNotification.builder().recipient(recipient).title(title).body(body).requestCode(requestCode).linkUrl(link).build());
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> mine() {
        return notifications.findTop50ByRecipientIdOrderByCreatedAtDesc(currentUser.id()).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public long unread() {
        return notifications.countByRecipientIdAndReadAtIsNull(currentUser.id());
    }

    @Transactional
    public NotificationResponse markRead(UUID id) {
        InAppNotification item = notifications.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notification", "id", id));
        if (!item.getRecipient().getId().equals(currentUser.id())) {
            throw new AccessDeniedException("Notification does not belong to current user");
        }
        if (item.getReadAt() == null) {
            item.setReadAt(LocalDateTime.now());
        
        }return response(notifications.save(item));
    }

    private NotificationResponse response(InAppNotification n) {
        return NotificationResponse.builder().id(n.getId()).title(n.getTitle()).body(n.getBody()).requestCode(n.getRequestCode()).linkUrl(n.getLinkUrl()).read(n.getReadAt() != null).createdAt(n.getCreatedAt()).build();
    }
}
