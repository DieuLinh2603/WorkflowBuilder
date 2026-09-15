package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.form.FormCreateRequest;
import com.company.workflowbuilder.dto.request.form.FormVersionUpdateRequest;
import com.company.workflowbuilder.dto.response.form.FormSummaryResponse;
import com.company.workflowbuilder.dto.response.form.FormVersionResponse;
import com.company.workflowbuilder.service.FormService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/forms")
public class FormController {

    private final FormService service;
    private final ObjectMapper mapper;

    public FormController(FormService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public List<FormSummaryResponse> list() {
        return service.list().stream()
                .map(m -> mapper.convertValue(m, FormSummaryResponse.class))
                .toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','WORKFLOW_OWNER')")
    public FormSummaryResponse get(@PathVariable UUID id) {
        return mapper.convertValue(service.get(id), FormSummaryResponse.class);
    }

    @GetMapping("/versions/{versionId}")
    @PreAuthorize("isAuthenticated()")
    public FormVersionResponse version(@PathVariable UUID versionId) {
        return mapper.convertValue(service.versionViewById(versionId), FormVersionResponse.class);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FormVersionResponse> create(@Valid @RequestBody FormCreateRequest body) {
        Map<String, Object> map = mapper.convertValue(body, new TypeReference<>() {});
        autoFillFieldKeys(map);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.convertValue(service.create(map), FormVersionResponse.class));
    }

    @PutMapping("/{formId}/versions/{versionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public FormVersionResponse update(
            @PathVariable UUID formId,
            @PathVariable UUID versionId,
            @Valid @RequestBody FormVersionUpdateRequest body) {
        Map<String, Object> map = mapper.convertValue(body, new TypeReference<>() {});
        autoFillFieldKeys(map);
        return mapper.convertValue(service.updateVersion(formId, versionId, map), FormVersionResponse.class);
    }

    @PostMapping("/{formId}/versions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FormVersionResponse> next(@PathVariable UUID formId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(mapper.convertValue(service.createNextVersion(formId), FormVersionResponse.class));
    }

    @PostMapping("/{formId}/versions/{versionId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public FormVersionResponse publish(@PathVariable UUID formId, @PathVariable UUID versionId) {
        return mapper.convertValue(service.publish(formId, versionId), FormVersionResponse.class);
    }

    private void autoFillFieldKeys(Map<String, Object> map) {
        if (map.containsKey("fields") && map.get("fields") instanceof List<?> fieldList) {
            fieldList.forEach(f -> {
                if (f instanceof Map<?, ?> fm) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fieldMap = (Map<String, Object>) fm;
                    Object key = fieldMap.get("fieldKey");
                    if (key == null || String.valueOf(key).trim().isBlank()) {
                        fieldMap.put("fieldKey", UUID.randomUUID().toString().replace("-", "").substring(0, 12));
                    }
                }
            });
        }
    }
}
