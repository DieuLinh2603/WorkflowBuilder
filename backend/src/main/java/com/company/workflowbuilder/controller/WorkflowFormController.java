package com.company.workflowbuilder.controller;
import com.company.workflowbuilder.service.WorkflowFormService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/workflows/{workflowId}") @RequiredArgsConstructor
public class WorkflowFormController {
    private final WorkflowFormService service;
    @PutMapping("/form") @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public Map<String,Object> bind(@PathVariable UUID workflowId,@RequestBody Map<String,Object> body){
        Object value=body.get("formVersionId");if(value==null)throw new IllegalArgumentException("formVersionId is required");
        return service.bind(workflowId,UUID.fromString(String.valueOf(value)));
    }
    @GetMapping("/submission-definition") @PreAuthorize("isAuthenticated()")
    public Map<String,Object> submission(@PathVariable UUID workflowId){return service.submissionDefinition(workflowId);}
}
