package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.ConnectionUpsertRequest;
import com.company.workflowbuilder.dto.response.ConnectionResponse;
import com.company.workflowbuilder.service.WorkflowConnectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflows/{workflowId}/connections")
public class WorkflowConnectionController {
    private final WorkflowConnectionService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<ConnectionResponse> list(@PathVariable UUID workflowId) {
        return service.list(workflowId);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public ResponseEntity<ConnectionResponse> create(@PathVariable UUID workflowId,
            @Valid @RequestBody ConnectionUpsertRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(workflowId, request));
    }

    @PutMapping("/{connectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public ConnectionResponse update(@PathVariable UUID workflowId, @PathVariable UUID connectionId,
            @Valid @RequestBody ConnectionUpsertRequest request) {
        return service.update(workflowId, connectionId, request);
    }

    @DeleteMapping("/{connectionId}")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public ResponseEntity<Void> delete(@PathVariable UUID workflowId, @PathVariable UUID connectionId) {
        service.delete(workflowId, connectionId);
        return ResponseEntity.noContent().build();
    }
}
