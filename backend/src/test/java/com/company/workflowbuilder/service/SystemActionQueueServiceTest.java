package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.data.DataConnector;
import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.entity.workflow.*;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.runtime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SystemActionQueueServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SystemActionExecutionRepository executions = mock(SystemActionExecutionRepository.class);
    private final DataConnectorRepository connectors = mock(DataConnectorRepository.class);
    private final WorkflowJsonCodec codec = new WorkflowJsonCodec(mapper);
    private final SystemActionQueueService service = new SystemActionQueueService(executions, connectors, codec, mapper);

    @Test void rendersSafeConnectorRequestAndDurablyStoresIt() throws Exception {
        UUID connectorId = UUID.randomUUID();
        DataConnector connector = DataConnector.builder().id(connectorId).connectorType("REST").active(true)
                .configJson("{\"baseUrl\":\"https://api.example.com/v1\"}").build();
        when(connectors.findById(connectorId)).thenReturn(Optional.of(connector));
        when(executions.save(any())).thenAnswer(invocation -> {
            SystemActionExecution value = invocation.getArgument(0);
            if (value.getId() == null) value.setId(UUID.randomUUID());
            return value;
        });
        Workflow workflow = Workflow.builder().name("Orders").build();
        WorkflowInstance instance = WorkflowInstance.builder().requestCode("REQ-1").workflow(workflow)
                .fieldSnapshot("{\"orderId\":\"A 1\"}").build();
        WorkflowStep step = WorkflowStep.builder().workflow(workflow).configJson(mapper.writeValueAsString(Map.of(
                "actionType", "API_CALL", "connectorId", connectorId, "httpMethod", "POST",
                "pathTemplate", "orders/{{orderId}}", "queryParams", List.of(Map.of("name", "source", "valueTemplate", "workflow {{orderId}}")),
                "headers", List.of(Map.of("name", "X-Tenant", "valueTemplate", "{{orderId}}")),
                "payloadTemplate", "{\"id\":\"{{orderId}}\"}", "responseMappings", List.of(),
                "responseSelection", Map.of("mode", "FILTER_LAST", "collectionJsonPath", "$.items",
                        "filterJsonPath", "$.id", "expectedValueTemplate", "{{orderId}}"),
                "maxAttempts", 2))).build();

        SystemActionExecution execution = service.enqueue(instance, step, Map.of("orderId", "A 1"), null);

        assertThat(execution.getRequestUrl()).isEqualTo("https://api.example.com/v1/orders/A%201?source=workflow%20A%201");
        assertThat(execution.getRequestBody()).isEqualTo("{\"id\":\"A 1\"}");
        assertThat(codec.snapshot(execution.getRequestHeadersJson())).containsEntry("X-Tenant", "A 1")
                .containsKey("Idempotency-Key").containsKey("X-Workflow-Execution-Id");
        assertThat(execution.getMaxAttempts()).isEqualTo(2);
        assertThat(codec.snapshot(execution.getResponseSelectorJson()))
                .containsEntry("mode", "FILTER_LAST").containsEntry("expectedValue", "A 1")
                .doesNotContainKey("expectedValueTemplate");
    }

    @Test void rejectsPathThatOverridesConnectorHost() throws Exception {
        UUID connectorId = UUID.randomUUID();
        when(connectors.findById(connectorId)).thenReturn(Optional.of(DataConnector.builder().id(connectorId)
                .connectorType("REST").active(true).configJson("{\"baseUrl\":\"https://api.example.com\"}").build()));
        WorkflowInstance instance = WorkflowInstance.builder().requestCode("REQ-1")
                .workflow(Workflow.builder().name("Orders").build()).fieldSnapshot("{}").build();
        WorkflowStep step = WorkflowStep.builder().configJson(mapper.writeValueAsString(Map.of(
                "actionType", "API_CALL", "connectorId", connectorId, "httpMethod", "GET",
                "pathTemplate", "https://evil.example/items"))).build();
        assertThatThrownBy(() -> service.enqueue(instance, step, Map.of(), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("relative");
        verifyNoInteractions(executions);
    }
}
