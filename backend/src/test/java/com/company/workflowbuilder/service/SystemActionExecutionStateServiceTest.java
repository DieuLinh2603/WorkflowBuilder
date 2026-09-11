package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.repository.SystemActionExecutionRepository;
import com.company.workflowbuilder.service.runtime.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SystemActionExecutionStateServiceTest {
    @Test void claimAndRetryPersistDurableState() {
        SystemActionExecutionRepository repository = mock(SystemActionExecutionRepository.class);
        SystemActionExecution execution = SystemActionExecution.builder().id(UUID.randomUUID())
                .status(SystemActionExecutionStatus.QUEUED).attemptCount(0).maxAttempts(3)
                .nextAttemptAt(LocalDateTime.now().minusSeconds(1)).build();
        when(repository.findById(execution.getId())).thenReturn(Optional.of(execution));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        SystemActionExecutionStateService service = new SystemActionExecutionStateService(repository);

        assertThat(service.claim(execution.getId())).isPresent();
        assertThat(execution.getStatus()).isEqualTo(SystemActionExecutionStatus.RUNNING);
        assertThat(execution.getAttemptCount()).isEqualTo(1);
        service.retry(execution.getId(), new RestConnectorHttpClient.Result(503, "busy", "API returned HTTP 503", true));
        assertThat(execution.getStatus()).isEqualTo(SystemActionExecutionStatus.RETRY_WAIT);
        assertThat(execution.getResponseStatus()).isEqualTo(503);
        assertThat(execution.getNextAttemptAt()).isAfter(LocalDateTime.now());
    }
}
