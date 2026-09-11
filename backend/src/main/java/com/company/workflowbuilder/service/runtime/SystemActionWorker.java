package com.company.workflowbuilder.service.runtime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemActionWorker {
    private final SystemActionExecutionStateService state;
    private final SystemActionCompletionService completion;
    private final SystemActionResumeService resume;
    private final RestConnectorHttpClient client;

    @Scheduled(fixedDelayString = "${app.workflow.system-action-worker-ms:5000}")
    @SchedulerLock(name = "systemActionWorker", lockAtMostFor = "PT6M", lockAtLeastFor = "PT1S")
    public void tick() {
        state.recoverStale();
        for (var id : resume.pending()) try { resume.resume(id); }
        catch (RuntimeException ex) { log.error("Cannot resume System Action execution {}", id, ex); }
        for (var id : state.due().stream().limit(1).toList()) state.claim(id).ifPresent(execution -> {
            var result = client.execute(execution, execution.getTimeoutSeconds());
            if (result.error() != null && result.retryable()
                    && execution.getAttemptCount() < execution.getMaxAttempts()) state.retry(id, result);
            else {
                completion.complete(id, result);
                try { resume.resume(id); }
                catch (RuntimeException ex) { log.error("Cannot resume System Action execution {}", id, ex); }
            }
        });
    }
}
