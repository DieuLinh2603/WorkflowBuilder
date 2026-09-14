package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.runtime.WorkflowInstance;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.repository.*;
import com.company.workflowbuilder.service.runtime.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemActionCalculationTest {
    private final WorkflowJsonCodec codec = new WorkflowJsonCodec(new ObjectMapper());
    private final WorkflowInstanceRepository instances = mock(WorkflowInstanceRepository.class);
    private final WorkflowSystemActionExecutor executor = new WorkflowSystemActionExecutor(instances,
            mock(InstanceStepLogRepository.class), mock(NotificationCenterService.class),
            mock(NotificationStepDeliveryService.class), codec, mock(SystemActionQueueService.class));

    @Test void systemActionWritesNumericOutputsWithBatchTotals() {
        var instance = WorkflowInstance.builder().fieldSnapshot("{\"amount\":20}").build();
        var step = WorkflowStep.builder().configJson("""
                {"actionType":"CALCULATE_OUTPUT","calculatedOutputs":[
                  {"fieldKey":"total","label":"Total","formula":"SUM([amount])"},
                  {"fieldKey":"ratio","label":"Ratio","formula":"[amount] / [total]"}]}
                """).build();
        assertThat(executor.execute(instance, step, List.of(Map.of("amount", 20), Map.of("amount", 60)))).isTrue();
        assertThat(codec.snapshot(instance.getFieldSnapshot())).containsEntry("total", 80).containsEntry("ratio", 0.25);
        verify(instances).save(instance);
    }

    @Test void failedCalculationDoesNotWriteAnyOutput() {
        var instance = WorkflowInstance.builder().fieldSnapshot("{\"amount\":20}").build();
        var step = WorkflowStep.builder().configJson("""
                {"actionType":"CALCULATE_OUTPUT","calculatedOutputs":[
                  {"fieldKey":"valid","label":"Valid","formula":"[amount] + 1"},
                  {"fieldKey":"invalid","label":"Invalid","formula":"[amount] / 0"}]}
                """).build();
        assertThat(executor.execute(instance, step)).isFalse();
        assertThat(instance.getFieldSnapshot()).isEqualTo("{\"amount\":20}");
        verifyNoInteractions(instances);
    }
}
