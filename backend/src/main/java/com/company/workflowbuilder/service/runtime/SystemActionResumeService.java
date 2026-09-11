package com.company.workflowbuilder.service.runtime;

import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.repository.SystemActionExecutionRepository;
import com.company.workflowbuilder.service.WorkflowEngineService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SystemActionResumeService {
    private final SystemActionExecutionRepository executions;
    private final WorkflowEngineService engine;
    private final ObjectMapper mapper;

    @Transactional(readOnly = true)
    public List<UUID> pending() {
        return executions.findTop50ByStatusInAndResumedAtIsNullOrderByCompletedAtAsc(
                List.of(SystemActionExecutionStatus.SUCCEEDED, SystemActionExecutionStatus.FAILED))
                .stream().map(SystemActionExecution::getId).toList();
    }

    @Transactional
    public void resume(UUID id) {
        SystemActionExecution execution = executions.findById(id).orElseThrow();
        if (execution.getResumedAt() != null || (execution.getStatus() != SystemActionExecutionStatus.SUCCEEDED
                && execution.getStatus() != SystemActionExecutionStatus.FAILED)) return;
        Map<String, Object> outputs;
        try { outputs = mapper.readValue(execution.getMappedOutputsJson(), new TypeReference<>() {}); }
        catch (Exception ex) { throw new IllegalStateException("Stored System Action outputs are invalid", ex); }
        engine.resumeSystemAction(execution, execution.getStatus() == SystemActionExecutionStatus.SUCCEEDED, outputs);
        execution.setResumedAt(LocalDateTime.now());
        executions.save(execution);
    }
}
