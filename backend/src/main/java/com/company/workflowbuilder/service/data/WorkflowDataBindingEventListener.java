package com.company.workflowbuilder.service.data;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowDataBindingEventListener {
    private final WorkflowDataBindingExecutionService executionService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void consumeAutomaticBindings(DatasetVersionPublishedEvent event) {
        for (UUID bindingId : executionService.automaticBindingIds(event.datasetVersionId())) {
            try {
                executionService.consume(bindingId, event.datasetVersionId());
            } catch (Exception exception) {
                log.error("Automatic workflow binding {} failed for dataset version {}: {}",
                        bindingId, event.datasetVersionId(), exception.getMessage(), exception);
                try {
                    executionService.notifyAutomaticFailure(bindingId, exception.getMessage());
                } catch (Exception notificationException) {
                    log.error("Could not notify owner about failed workflow binding {}", bindingId,
                            notificationException);
                }
            }
        }
    }
}
