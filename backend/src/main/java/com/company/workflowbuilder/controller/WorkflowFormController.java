package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.form.BindFormVersionRequest;
import com.company.workflowbuilder.service.WorkflowFormService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/workflows/{workflowId}")
public class WorkflowFormController {

    private final WorkflowFormService service;

    public WorkflowFormController(WorkflowFormService service) {
        this.service = service;
    }

    @PutMapping("/form")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public Map<String, Object> bind(
            @PathVariable UUID workflowId,
            @Valid @RequestBody BindFormVersionRequest body) {
        return service.bind(workflowId, body.getFormVersionId());
    }

    @GetMapping("/submission-definition")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> submission(@PathVariable UUID workflowId) {
        return service.submissionDefinition(workflowId);
    }
}
