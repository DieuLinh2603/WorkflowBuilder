package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.runtime.*;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.company.workflowbuilder.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeadlineReminderServiceTest {
    @Test void sendsOnceForTaskInsideGlobalWindow() {
        WorkflowTaskRepository tasks=mock(WorkflowTaskRepository.class);ReminderDeliveryRepository deliveries=mock(ReminderDeliveryRepository.class);
        SystemSettingService settings=mock(SystemSettingService.class);NotificationCenterService notifications=mock(NotificationCenterService.class);
        WorkflowStep step=WorkflowStep.builder().configJson("{\"reminderChannels\":[\"EMAIL\"]}").build();
        WorkflowInstance instance=WorkflowInstance.builder().requestCode("REQ-1").build();
        WorkflowTask task=WorkflowTask.builder().id(UUID.randomUUID()).instance(instance).step(step).assignee(new User()).deadlineAt(LocalDateTime.now().plusHours(5)).build();
        when(settings.deadlineLeadHours()).thenReturn(24);when(tasks.findByStatusAndDeadlineAtBetween(eq(TaskStatus.PENDING),any(),any())).thenReturn(List.of(task));
        when(deliveries.existsByTaskIdAndChannelAndDeadlineAt(any(),anyString(),any())).thenReturn(false);
        new DeadlineReminderService(tasks,deliveries,settings,notifications,new ObjectMapper()).sendDueReminders();
        verify(notifications).create(eq(task.getAssignee()),eq("Task sắp tới hạn"),anyString(),eq("REQ-1"),anyString());
        verify(deliveries,times(2)).save(any());
    }

    @Test void doesNothingWhenRepositoryFindsNoDueTask() {
        WorkflowTaskRepository tasks=mock(WorkflowTaskRepository.class);ReminderDeliveryRepository deliveries=mock(ReminderDeliveryRepository.class);
        SystemSettingService settings=mock(SystemSettingService.class);NotificationCenterService notifications=mock(NotificationCenterService.class);
        when(settings.deadlineLeadHours()).thenReturn(24);when(tasks.findByStatusAndDeadlineAtBetween(eq(TaskStatus.PENDING),any(),any())).thenReturn(List.of());
        new DeadlineReminderService(tasks,deliveries,settings,notifications,new ObjectMapper()).sendDueReminders();
        verifyNoInteractions(notifications,deliveries);
    }

    @Test void createsActorInAppReminderWithoutExternalChannels() {
        WorkflowTaskRepository tasks=mock(WorkflowTaskRepository.class);ReminderDeliveryRepository deliveries=mock(ReminderDeliveryRepository.class);
        SystemSettingService settings=mock(SystemSettingService.class);NotificationCenterService notifications=mock(NotificationCenterService.class);
        WorkflowStep step=WorkflowStep.builder().configJson("{}").build();
        WorkflowInstance instance=WorkflowInstance.builder().requestCode("REQ-2").build();
        WorkflowTask task=WorkflowTask.builder().id(UUID.randomUUID()).instance(instance).step(step).assignee(new User()).deadlineAt(LocalDateTime.now().plusHours(2)).build();
        when(settings.deadlineLeadHours()).thenReturn(24);when(tasks.findByStatusAndDeadlineAtBetween(eq(TaskStatus.PENDING),any(),any())).thenReturn(List.of(task));
        when(deliveries.existsByTaskIdAndChannelAndDeadlineAt(task.getId(),"IN_APP",task.getDeadlineAt())).thenReturn(false);
        new DeadlineReminderService(tasks,deliveries,settings,notifications,new ObjectMapper()).sendDueReminders();
        verify(notifications).create(eq(task.getAssignee()),eq("Task sắp tới hạn"),anyString(),eq("REQ-2"),eq("/tasks/"+task.getId()));
        verify(deliveries).save(any());
    }
}
