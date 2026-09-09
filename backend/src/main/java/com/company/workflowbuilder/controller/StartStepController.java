package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.CustomFieldCreateRequest;
import com.company.workflowbuilder.dto.request.StartStepConfigRequest;
import com.company.workflowbuilder.dto.response.CustomFieldResponse;
import com.company.workflowbuilder.dto.response.StartStepConfigResponse;
import com.company.workflowbuilder.service.StartStepService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/workflows/{workflowId}/steps/{stepId}")
@RequiredArgsConstructor
@Tag(name = "Start Step Configuration", description = "Manage instructions and custom fields for Start Step")
public class StartStepController {

    private final StartStepService startStepService;

    @GetMapping("/fields")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get custom fields configured for a workflow step")
    public ResponseEntity<List<CustomFieldResponse>> getFields(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId) {
        return ResponseEntity.ok(startStepService.getCustomFields(workflowId, stepId));
    }

    @GetMapping("/start-config")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get start step configuration and custom fields")
    public ResponseEntity<StartStepConfigResponse> getStartConfig(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId) {
        return ResponseEntity.ok(startStepService.getStartConfig(workflowId, stepId));
    }

    @PutMapping("/start-config")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Update start step instructions and requester scope")
    public ResponseEntity<Void> updateStartConfig(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId,
            @Valid @RequestBody StartStepConfigRequest request) {
        startStepService.updateStartConfig(workflowId, stepId, request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/fields")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Add a new custom field to Start Step")
    public ResponseEntity<CustomFieldResponse> createField(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId,
            @Valid @RequestBody CustomFieldCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(startStepService.createCustomField(workflowId, stepId, request));
    }

    @PutMapping("/fields/{fieldId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Update an existing custom field")
    public ResponseEntity<CustomFieldResponse> updateField(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId,
            @PathVariable UUID fieldId,
            @Valid @RequestBody CustomFieldCreateRequest request) {
        return ResponseEntity.ok(startStepService.updateCustomField(workflowId, stepId, fieldId, request));
    }

    @DeleteMapping("/fields/{fieldId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Delete a custom field")
    public ResponseEntity<Void> deleteField(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId,
            @PathVariable UUID fieldId) {
        startStepService.deleteCustomField(workflowId, stepId, fieldId);
        return ResponseEntity.noContent().build();
    }
}
