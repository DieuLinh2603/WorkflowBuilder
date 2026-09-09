package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.notification.ReminderDelivery;
import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeadlineReminderService {
    private final WorkflowTaskRepository tasks;
    private final ReminderDeliveryRepository deliveries;
    private final SystemSettingService settings;
    private final NotificationCenterService notifications;
    private final ObjectMapper mapper;

    @Scheduled(fixedDelayString = "${app.workflow.reminder-scheduler-ms:300000}")
    @SchedulerLock(name = "deadlineReminder", lockAtLeastFor = "PT10S", lockAtMostFor = "PT4M")
    @Transactional
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(), until = now.plusHours(settings.deadlineLeadHours());
        for (WorkflowTask task : tasks.findByStatusAndDeadlineAtBetween(TaskStatus.PENDING, now, until)) {
            Set<String> channels = channels(task);
            if (!deliveries.existsByTaskIdAndChannelAndDeadlineAt(task.getId(), "IN_APP", task.getDeadlineAt())) {
                notifications.create(task.getAssignee(), "Task sắp tới hạn",
                        task.getInstance().getRequestCode() + " - hạn " + task.getDeadlineAt(),
                        task.getInstance().getRequestCode(), "/tasks/" + task.getId());
                deliveries.save(ReminderDelivery.builder().task(task).channel("IN_APP").deadlineAt(task.getDeadlineAt())
                        .status("SENT").build());
            }
            for (String channel : channels)
                if (!"IN_APP".equals(channel) && !deliveries.existsByTaskIdAndChannelAndDeadlineAt(task.getId(),
                        channel, task.getDeadlineAt())) {
                    log.info("Deadline reminder queued: task={}, channel={}", task.getId(), channel);
                    deliveries.save(ReminderDelivery.builder().task(task).channel(channel)
                            .deadlineAt(task.getDeadlineAt()).status("QUEUED").build());
                }
        }
    }

    private Set<String> channels(WorkflowTask task) {
        try {
            if (task.getStep().getConfigJson() == null)
                return Set.of();
            Map<String, Object> cfg = mapper.readValue(task.getStep().getConfigJson(), new TypeReference<>() {
            });
            Object raw = cfg.get("reminderChannels");
            if (!(raw instanceof Collection<?> values))
                return Set.of();
            Set<String> result = new HashSet<>();
            values.forEach(v -> result.add(v.toString()));
            return result;
        } catch (Exception e) {
            log.warn("Invalid reminder config for step {}", task.getStep().getId());
            return Set.of();
        }
    }
}
