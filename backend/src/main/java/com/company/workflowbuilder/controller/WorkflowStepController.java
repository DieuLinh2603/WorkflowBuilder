package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.StepCreateRequest;
import com.company.workflowbuilder.dto.request.StepUpdateRequest;
import com.company.workflowbuilder.dto.request.StepLayoutUpdateRequest;
import com.company.workflowbuilder.dto.response.WorkflowStepResponse;
import com.company.workflowbuilder.dto.response.StepDeleteResponse;
import com.company.workflowbuilder.service.WorkflowService;
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
@RequestMapping("/api/workflows/{workflowId}/steps")
@RequiredArgsConstructor
@Tag(name = "Workflow Steps", description = "Add/remove steps within a workflow")
public class WorkflowStepController {

    private final WorkflowService workflowService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Add a new step to a workflow")
    public ResponseEntity<WorkflowStepResponse> addStep(
            @PathVariable UUID workflowId,
            @Valid @RequestBody StepCreateRequest request) {
        WorkflowStepResponse response = workflowService.addStep(workflowId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Update a workflow step")
    public ResponseEntity<WorkflowStepResponse> updateStep(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId,
            @Valid @RequestBody StepUpdateRequest request) {
        return ResponseEntity.ok(workflowService.updateStep(workflowId, stepId, request));
    }

    @PutMapping("/layout")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Save one or more node positions on the workflow canvas")
    public ResponseEntity<List<WorkflowStepResponse>> updateLayout(
            @PathVariable UUID workflowId,
            @Valid @RequestBody StepLayoutUpdateRequest request) {
        return ResponseEntity.ok(workflowService.updateLayout(workflowId, request));
    }

    @DeleteMapping("/{stepId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Delete a step from a workflow (cannot delete START)")
    public ResponseEntity<StepDeleteResponse> deleteStep(
            @PathVariable UUID workflowId,
            @PathVariable UUID stepId,
            @RequestParam(required = false) UUID incomingConnectionId,
            @RequestParam(required = false) UUID outgoingConnectionId) {
        return ResponseEntity.ok(workflowService.deleteStep(
                workflowId, stepId, incomingConnectionId, outgoingConnectionId));
    }
}
