package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.response.NotificationResponse;
import com.company.workflowbuilder.service.NotificationCenterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationCenterService service;

    @GetMapping
    public List<NotificationResponse> mine() {
        return service.mine();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unread() {
        return Map.of("count", service.unread());
    }

    @PatchMapping("/{id}/read")
    public NotificationResponse read(@PathVariable UUID id) {
        return service.markRead(id);
    }
}
