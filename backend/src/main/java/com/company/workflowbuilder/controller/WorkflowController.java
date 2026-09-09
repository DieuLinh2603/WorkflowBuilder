package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.WorkflowCreateRequest;
import com.company.workflowbuilder.dto.response.WorkflowResponse;
import com.company.workflowbuilder.dto.response.WorkflowStepResponse;
import com.company.workflowbuilder.dto.response.CustomFieldResponse;
import com.company.workflowbuilder.dto.response.WorkflowVersionResponse;
import com.company.workflowbuilder.dto.response.WorkflowVersionComparisonResponse;
import com.company.workflowbuilder.service.WorkflowService;
import com.company.workflowbuilder.service.WorkflowValidationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import com.company.workflowbuilder.dto.request.DuplicateWorkflowRequest;
import com.company.workflowbuilder.entity.workflow.WorkflowStatus;

@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
@Tag(name = "Workflow", description = "Workflow CRUD & validation")
public class WorkflowController {

    private final WorkflowService workflowService;
    private final WorkflowValidationService validationService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER')")
    @Operation(summary = "Create a new workflow (DRAFT, v1.0, auto START step)")
    public ResponseEntity<WorkflowResponse> createWorkflow(
            @Valid @RequestBody WorkflowCreateRequest request) {
        WorkflowResponse response = workflowService.createWorkflow(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WorkflowResponse>> listWorkflows() {
        return ResponseEntity.ok(workflowService.listAccessible());
    }

    @GetMapping("/catalog")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WorkflowResponse>> listCatalog() {
        return ResponseEntity.ok(workflowService.listCatalog());
    }

    @GetMapping("/managed")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public ResponseEntity<List<WorkflowResponse>> listManagedWorkflows() {
        return ResponseEntity.ok(workflowService.listManaged());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get workflow detail by ID")
    public ResponseEntity<WorkflowResponse> getWorkflow(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getWorkflowById(id));
    }

    @GetMapping("/{id}/active")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkflowResponse> getActiveVersion(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getActiveVersion(id));
    }

    @GetMapping("/{id}/steps")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all steps of a workflow")
    public ResponseEntity<List<WorkflowStepResponse>> getSteps(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getStepsByWorkflowId(id));
    }

    @GetMapping("/{id}/fields")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get condition fields collected from every form step in a workflow")
    public ResponseEntity<List<CustomFieldResponse>> getFields(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getWorkflowFields(id));
    }

    @GetMapping("/{id}/versions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<WorkflowVersionResponse>> versions(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.getVersionHistory(id));
    }

    @GetMapping("/{id}/compare/{otherVersionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<WorkflowVersionComparisonResponse> compare(@PathVariable UUID id,
            @PathVariable UUID otherVersionId) {
        return ResponseEntity.ok(workflowService.compareVersions(id, otherVersionId));
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> restore(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.restoreVersion(id));
    }

    @PostMapping("/{id}/validate")
    @PreAuthorize("hasAnyRole('ADMIN', 'WORKFLOW_OWNER', 'EDITOR', 'VIEWER')")
    @Operation(summary = "Validate workflow structure, returns list of errors")
    public ResponseEntity<List<String>> validateWorkflow(@PathVariable UUID id) {
        return ResponseEntity.ok(validationService.validate(id));
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> duplicate(@PathVariable UUID id,
            @Valid @RequestBody DuplicateWorkflowRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.duplicate(id, request));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> publish(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.publish(id));
    }

    @PostMapping("/{id}/versions")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> nextVersion(@PathVariable UUID id) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workflowService.createNextDraft(id));
    }

    @DeleteMapping("/{id}/unchanged-draft")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<Void> discardUnchangedDraft(@PathVariable UUID id) {
        workflowService.discardUnchangedDraft(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/suspend")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> suspend(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.changeStatus(id, WorkflowStatus.SUSPENDED));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(workflowService.changeStatus(id, WorkflowStatus.ARCHIVED));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workflowService.deleteWorkflow(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/editors/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> addEditor(@PathVariable UUID id, @PathVariable UUID userId) {
        return ResponseEntity.ok(workflowService.addEditor(id, userId));
    }

    @DeleteMapping("/{id}/editors/{userId}")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public ResponseEntity<WorkflowResponse> removeEditor(@PathVariable UUID id, @PathVariable UUID userId) {
        return ResponseEntity.ok(workflowService.removeEditor(id, userId));
    }

    @PutMapping("/{id}/owner/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WorkflowResponse> transferOwner(@PathVariable UUID id, @PathVariable UUID userId) {
        return ResponseEntity.ok(workflowService.transferOwner(id, userId));
    }
}
