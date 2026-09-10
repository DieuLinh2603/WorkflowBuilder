package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.BusinessModuleRequest;
import com.company.workflowbuilder.dto.request.WorkflowTypeDefinitionRequest;
import com.company.workflowbuilder.dto.response.BusinessModuleResponse;
import com.company.workflowbuilder.dto.response.WorkflowTypeDefinitionResponse;
import com.company.workflowbuilder.service.WorkflowMetadataService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
@RequiredArgsConstructor
public class WorkflowMetadataController {
    private final WorkflowMetadataService service;

    @GetMapping("/modules")
    @PreAuthorize("isAuthenticated()")
    public List<BusinessModuleResponse> modules() { return service.activeModulesForCurrentUser(); }

    @GetMapping("/modules/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<BusinessModuleResponse> allModules() { return service.allModules(); }

    @PostMapping("/modules")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public BusinessModuleResponse createModule(@Valid @RequestBody BusinessModuleRequest request) {
        return service.createModule(request);
    }

    @PutMapping("/modules/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessModuleResponse updateModule(@PathVariable String code,
            @Valid @RequestBody BusinessModuleRequest request) { return service.updateModule(code, request); }

    @PatchMapping("/modules/{code}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public BusinessModuleResponse moduleStatus(@PathVariable String code, @RequestParam boolean active) {
        return service.setModuleActive(code, active);
    }

    @GetMapping("/workflow-types")
    @PreAuthorize("isAuthenticated()")
    public List<WorkflowTypeDefinitionResponse> types() { return service.activeTypes(); }

    @GetMapping("/workflow-types/all")
    @PreAuthorize("hasRole('ADMIN')")
    public List<WorkflowTypeDefinitionResponse> allTypes() { return service.allTypes(); }

    @PostMapping("/workflow-types")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkflowTypeDefinitionResponse createType(@Valid @RequestBody WorkflowTypeDefinitionRequest request) {
        return service.createType(request);
    }

    @PutMapping("/workflow-types/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkflowTypeDefinitionResponse updateType(@PathVariable String code,
            @Valid @RequestBody WorkflowTypeDefinitionRequest request) { return service.updateType(code, request); }

    @PatchMapping("/workflow-types/{code}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public WorkflowTypeDefinitionResponse typeStatus(@PathVariable String code, @RequestParam boolean active) {
        return service.setTypeActive(code, active);
    }
}
