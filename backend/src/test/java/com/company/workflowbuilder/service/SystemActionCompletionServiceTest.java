package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.repository.SystemActionExecutionRepository;
import com.company.workflowbuilder.service.runtime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SystemActionCompletionServiceTest {
    @Test void mapsJsonResponseBeforeResumingWorkflow() {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowJsonCodec codec = new WorkflowJsonCodec(mapper);
        SystemActionExecutionRepository executions = mock(SystemActionExecutionRepository.class);
        WorkflowEngineService engine = mock(WorkflowEngineService.class);
        SystemActionExecution execution = SystemActionExecution.builder().id(UUID.randomUUID())
                .status(SystemActionExecutionStatus.RUNNING)
                .responseMappingsJson("[{\"jsonPath\":\"$.data.id\",\"targetField\":\"orderId\",\"required\":true}]")
                .build();
        when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));
        SystemActionCompletionService service = new SystemActionCompletionService(executions, codec, mapper);

        service.complete(execution.getId(), new RestConnectorHttpClient.Result(201, "{\"data\":{\"id\":\"123\"}}", null, false));

        assertThat(execution.getStatus()).isEqualTo(SystemActionExecutionStatus.SUCCEEDED);
        assertThat(codec.snapshot(execution.getMappedOutputsJson())).containsEntry("orderId", "123");
        SystemActionResumeService resume = new SystemActionResumeService(executions, engine, mapper);
        resume.resume(execution.getId());
        verify(engine).resumeSystemAction(same(execution), eq(true), eq(Map.of("orderId", "123")));
        assertThat(execution.getResumedAt()).isNotNull();
    }

    @Test void requiredMappingFailureDoesNotRequestAnotherHttpAttempt() {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowJsonCodec codec = new WorkflowJsonCodec(mapper);
        SystemActionExecutionRepository executions = mock(SystemActionExecutionRepository.class);
        WorkflowEngineService engine = mock(WorkflowEngineService.class);
        SystemActionExecution execution = SystemActionExecution.builder().id(UUID.randomUUID())
                .status(SystemActionExecutionStatus.RUNNING)
                .responseMappingsJson("[{\"jsonPath\":\"$.missing\",\"targetField\":\"orderId\",\"required\":true}]")
                .build();
        when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));
        new SystemActionCompletionService(executions, codec, mapper).complete(execution.getId(),
                new RestConnectorHttpClient.Result(200, "{\"data\":{}}", null, false));

        assertThat(execution.getStatus()).isEqualTo(SystemActionExecutionStatus.FAILED);
        assertThat(execution.getErrorMessage()).contains("Required JSONPath");
        new SystemActionResumeService(executions, engine, mapper).resume(execution.getId());
        verify(engine).resumeSystemAction(same(execution), eq(false), eq(Map.of()));
    }

    @Test void filtersArrayAndMapsFieldsFromLastMatchingItem() {
        ObjectMapper mapper = new ObjectMapper();
        WorkflowJsonCodec codec = new WorkflowJsonCodec(mapper);
        SystemActionExecutionRepository executions = mock(SystemActionExecutionRepository.class);
        SystemActionExecution execution = SystemActionExecution.builder().id(UUID.randomUUID())
                .status(SystemActionExecutionStatus.RUNNING)
                .responseSelectorJson("{\"mode\":\"FILTER_LAST\",\"collectionJsonPath\":\"$.data.items\",\"filterJsonPath\":\"$.status\",\"expectedValue\":\"ACTIVE\"}")
                .responseMappingsJson("[{\"jsonPath\":\"$.id\",\"targetField\":\"orderId\",\"required\":true}]")
                .build();
        when(executions.findById(execution.getId())).thenReturn(Optional.of(execution));

        new SystemActionCompletionService(executions, codec, mapper).complete(execution.getId(),
                new RestConnectorHttpClient.Result(200,
                        "{\"data\":{\"items\":[{\"id\":\"1\",\"status\":\"ACTIVE\"},{\"id\":\"2\",\"status\":\"OFF\"},{\"id\":\"3\",\"status\":\"ACTIVE\"}]}}",
                        null, false));

        assertThat(execution.getStatus()).isEqualTo(SystemActionExecutionStatus.SUCCEEDED);
        assertThat(codec.snapshot(execution.getMappedOutputsJson())).containsEntry("orderId", "3");
    }
}
