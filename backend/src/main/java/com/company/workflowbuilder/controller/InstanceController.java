package com.company.workflowbuilder.controller;

import com.company.workflowbuilder.dto.request.*;
import com.company.workflowbuilder.dto.response.*;
import com.company.workflowbuilder.entity.runtime.TaskStatus;
import com.company.workflowbuilder.service.WorkflowEngineService;
import com.company.workflowbuilder.service.runtime.SystemActionQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class InstanceController {
    private final WorkflowEngineService engine;
    private final SystemActionQueryService systemActions;

    @PostMapping({"/instances","/tickets"})
    public ResponseEntity<InstanceResponse> submit(@Valid @RequestBody CreateInstanceRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(engine.submit(r));
    }

    @PostMapping({"/instances/batch","/tickets/batch"})
    public ResponseEntity<BatchInstanceResponse> submitBatch(@Valid @RequestBody CreateBatchInstanceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(engine.submitBatch(request));
    }

    @GetMapping("/workflows/{workflowId}/request-draft")
    public ResponseEntity<Map<String, Object>> requestDraft(@PathVariable UUID workflowId) {
        return engine.requestDraft(workflowId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/workflows/{workflowId}/request-draft")
    public Map<String, Object> saveRequestDraft(@PathVariable UUID workflowId,
            @Valid @RequestBody CreateInstanceRequest request) {
        return engine.saveRequestDraft(workflowId, request);
    }

    @DeleteMapping("/workflows/{workflowId}/request-draft")
    public ResponseEntity<Void> deleteRequestDraft(@PathVariable UUID workflowId) {
        engine.deleteRequestDraft(workflowId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping({"/instances/mine","/tickets/mine"})
    public List<InstanceResponse> mine() {
        return engine.mine();
    }

    @GetMapping({"/instances","/tickets"})
    public List<InstanceResponse> instances() {
        return engine.accessibleInstances();
    }

    @GetMapping({"/instances/{id}","/tickets/{id}"})
    public InstanceResponse get(@PathVariable UUID id) {
        return engine.get(id);
    }

    @GetMapping({"/instances/{id}/history","/tickets/{id}/history"})
    public List<InstanceHistoryResponse> history(@PathVariable UUID id) {
        return engine.history(id);
    }

    @GetMapping({"/instances/{id}/system-actions","/tickets/{id}/system-actions"})
    public List<Map<String, Object>> systemActions(@PathVariable UUID id) {
        return systemActions.forInstance(id);
    }

    @GetMapping({"/instances/{id}/batch-records","/tickets/{id}/batch-records"})
    public Map<String, Object> batchRecords(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        return engine.batchRecords(id, page, size);
    }

    @GetMapping({"/instances/{id}/batch-records/{rowNumber}","/tickets/{id}/batch-records/{rowNumber}"})
    public Map<String, Object> batchRecord(@PathVariable UUID id, @PathVariable int rowNumber) {
        return engine.batchRecord(id, rowNumber);
    }

    @PostMapping({"/instances/{id}/approve","/tickets/{id}/approve"})
    public InstanceResponse approve(@PathVariable UUID id, @RequestBody(required = false) TaskActionRequest r) {
        return engine.act(id, "APPROVE", r == null ? new TaskActionRequest() : r);
    }

    @PostMapping({"/instances/{id}/reject","/tickets/{id}/reject"})
    public InstanceResponse reject(@PathVariable UUID id, @RequestBody(required = false) TaskActionRequest r) {
        return engine.act(id, "REJECT", r == null ? new TaskActionRequest() : r);
    }

    @PostMapping({"/instances/{id}/complete","/tickets/{id}/complete"})
    public InstanceResponse complete(@PathVariable UUID id, @RequestBody(required = false) TaskActionRequest r) {
        return engine.act(id, "COMPLETE", r == null ? new TaskActionRequest() : r);
    }

    @PostMapping({"/instances/{id}/re-evaluate","/tickets/{id}/re-evaluate"})
    public InstanceResponse reEvaluate(@PathVariable UUID id) {
        return engine.reEvaluate(id);
    }

    @PostMapping({"/instances/{id}/cancel","/tickets/{id}/cancel"})
    public InstanceResponse cancel(@PathVariable UUID id) {
        return engine.cancel(id);
    }

    @PostMapping({"/instances/{id}/withdraw","/tickets/{id}/withdraw"})
    public InstanceResponse withdraw(@PathVariable UUID id, @RequestBody TaskActionRequest request) {
        return engine.withdraw(id, request);
    }

    @PostMapping("/tasks/{taskId}/preview-outputs")
    public List<Map<String, Object>> previewOutputs(@PathVariable UUID taskId, @RequestBody TaskActionRequest request) {
        return engine.previewTaskOutputs(taskId, request);
    }

    @PostMapping("/tasks/{taskId}/{action}")
    public InstanceResponse actTask(@PathVariable UUID taskId, @PathVariable String action,
            @RequestBody(required = false) TaskActionRequest request) {
        return engine.actTask(taskId, action, request == null ? new TaskActionRequest() : request);
    }

    @GetMapping("/my-tasks")
    public List<TaskResponse> tasks(@RequestParam(defaultValue = "PENDING") TaskStatus status) {
        return engine.myTasks(status);
    }

    @GetMapping("/my-tasks/{taskId}")
    public TaskResponse task(@PathVariable UUID taskId) {
        return engine.myTask(taskId);
    }
}
