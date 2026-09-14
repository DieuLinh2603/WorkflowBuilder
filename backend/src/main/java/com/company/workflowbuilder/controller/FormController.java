package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.service.FormService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/forms") @RequiredArgsConstructor
public class FormController {
    private final FormService service;
    @GetMapping @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')") public List<Map<String,Object>> list(){return service.list();}
    @GetMapping("/{id}") @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')") public Map<String,Object> get(@PathVariable UUID id){return service.get(id);}
    @GetMapping("/versions/{versionId}") @PreAuthorize("isAuthenticated()") public Map<String,Object> version(@PathVariable UUID versionId){return service.versionViewById(versionId);}
    @PostMapping @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Map<String,Object>> create(@RequestBody Map<String,Object> body){return ResponseEntity.status(201).body(service.create(body));}
    @PutMapping("/{formId}/versions/{versionId}") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> update(@PathVariable UUID formId,@PathVariable UUID versionId,@RequestBody Map<String,Object> body){return service.updateVersion(formId,versionId,body);}
    @PostMapping("/{formId}/versions") @PreAuthorize("hasRole('ADMIN')") public ResponseEntity<Map<String,Object>> next(@PathVariable UUID formId){return ResponseEntity.status(201).body(service.createNextVersion(formId));}
    @PostMapping("/{formId}/versions/{versionId}/publish") @PreAuthorize("hasRole('ADMIN')") public Map<String,Object> publish(@PathVariable UUID formId,@PathVariable UUID versionId){return service.publish(formId,versionId);}
}
