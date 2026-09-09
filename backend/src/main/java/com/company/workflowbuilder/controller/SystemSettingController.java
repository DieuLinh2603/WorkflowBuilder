package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.service.SystemSettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/settings")
@PreAuthorize("hasRole('ADMIN')")
public class SystemSettingController {
    private final SystemSettingService service;

    @GetMapping("/deadline-reminder")
    public Map<String, Integer> get() {
        return Map.of("leadTimeHours", service.deadlineLeadHours());
    }

    @PutMapping("/deadline-reminder")
    public Map<String, Integer> put(@RequestBody Map<String, Integer> body) {
        return Map.of("leadTimeHours", service.updateDeadlineLeadHours(body.getOrDefault("leadTimeHours", 24)));
    }

    @PutMapping("/reminder-endpoints")
    public Map<String, Boolean> endpoints(@RequestBody Map<String, String> body) {
        body.forEach(service::updateEndpoint);
        return Map.of("updated", true);
    }
}
