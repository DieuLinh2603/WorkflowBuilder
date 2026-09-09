package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.*;
import com.company.workflowbuilder.service.StepConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/workflows/{workflowId}/steps/{stepId}/config")
public class StepConfigController {
    private final StepConfigService service;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> get(@PathVariable UUID workflowId, @PathVariable UUID stepId) {
        return service.get(workflowId, stepId);
    }

    @PutMapping("/approval")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public Map<String, Object> approval(@PathVariable UUID workflowId, @PathVariable UUID stepId,
            @Valid @RequestBody ApprovalConfigRequest request) {
        return service.configureApproval(workflowId, stepId, request);
    }

    @PutMapping("/notification")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public Map<String, Object> notification(@PathVariable UUID workflowId, @PathVariable UUID stepId,
            @Valid @RequestBody NotificationStepConfigRequest request) {
        return service.configureNotification(workflowId, stepId, request);
    }

    @PutMapping("/review")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public Map<String, Object> review(@PathVariable UUID workflowId, @PathVariable UUID stepId,
            @Valid @RequestBody ReviewConfigRequest request) {
        return service.configureReview(workflowId, stepId, request);
    }

    @PutMapping("/assignment")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public Map<String, Object> assignment(@PathVariable UUID workflowId, @PathVariable UUID stepId,
            @Valid @RequestBody AssignmentConfigRequest request) {
        return service.configureAssignment(workflowId, stepId, request);
    }

    @PutMapping("/system-action")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public Map<String, Object> systemAction(@PathVariable UUID workflowId, @PathVariable UUID stepId,
            @Valid @RequestBody SystemActionConfigRequest request) {
        return service.configureSystemAction(workflowId, stepId, request);
    }

    @PutMapping("/end")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER','EDITOR','VIEWER')")
    public Map<String, Object> end(@PathVariable UUID workflowId, @PathVariable UUID stepId,
            @Valid @RequestBody EndStepConfigRequest request) {
        return service.configureEnd(workflowId, stepId, request);
    }
}
