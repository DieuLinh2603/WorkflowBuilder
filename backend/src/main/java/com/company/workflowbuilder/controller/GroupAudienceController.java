package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.AudienceRequest;
import com.company.workflowbuilder.entity.user.UserGroup;
import com.company.workflowbuilder.service.GroupAudienceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
public class GroupAudienceController {
    private final GroupAudienceService service;

    @GetMapping("/api/groups")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public List<Map<String, Object>> groups() {
        return service.listGroups();
    }

    @GetMapping("/api/groups/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public Map<String, Object> group(@PathVariable UUID id) {
        return service.groupDetail(id);
    }

    @PostMapping("/api/groups")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> create(@RequestBody Map<String, String> b) {
        UserGroup g = service.createGroup(b.get("name"));
        return Map.of("id", g.getId(), "name", g.getName());
    }

    @PutMapping("/api/groups/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> member(@PathVariable UUID id, @PathVariable UUID userId) {
        UserGroup g = service.addMember(id, userId);
        return Map.of("id", g.getId(), "name", g.getName(), "memberCount", g.getMembers().size());
    }

    @DeleteMapping("/api/groups/{id}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> removeMember(@PathVariable UUID id, @PathVariable UUID userId) {
        UserGroup g = service.removeMember(id, userId);
        return Map.of("id", g.getId(), "name", g.getName(), "memberCount", g.getMembers().size());
    }

    @GetMapping("/api/workflows/{id}/audience")
    public List<Map<String, Object>> audience(@PathVariable UUID id) {
        return service.audience(id);
    }

    @PutMapping("/api/workflows/{id}/audience")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public List<Map<String, Object>> audience(@PathVariable UUID id, @Valid @RequestBody List<AudienceRequest> body) {
        service.replaceAudience(id, body);
        return service.audience(id);
    }
}
