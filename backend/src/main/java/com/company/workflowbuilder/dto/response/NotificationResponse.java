package com.company.workflowbuilder.dto.response;

import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class NotificationResponse {
    private UUID id;
    private String title;
    private String body;
    private String requestCode;
    private String linkUrl;
    private boolean read;
    private LocalDateTime createdAt;
}
