package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.SystemSetting;
import com.company.workflowbuilder.repository.SystemSettingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemSettingServiceTest {

    @Test
    void returnsMaskedEndpointStatusWithoutExposingSecretUrl() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        Environment environment = mock(Environment.class);
        String secretUrl = "https://hooks.example.com/private/token";
        when(repository.findById(SystemSettingService.TEAMS_WEBHOOK_URL))
                .thenReturn(Optional.of(new SystemSetting(SystemSettingService.TEAMS_WEBHOOK_URL, secretUrl)));
        when(repository.findById(SystemSettingService.WEBHOOK_URL)).thenReturn(Optional.empty());
        when(environment.getProperty("spring.mail.host", "")).thenReturn("");

        Map<String, Object> status = new SystemSettingService(repository, currentUser, environment)
                .reminderEndpointStatus();

        assertEquals(true, status.get("teamsWebhookConfigured"));
        assertEquals("SETTINGS", status.get("teamsWebhookSource"));
        assertFalse(String.valueOf(status.get("teamsWebhookDisplay")).contains("private/token"));
        assertEquals(false, status.get("smtpConfigured"));
    }

    @Test
    void usesEnvironmentEndpointWhenDatabaseSettingIsMissing() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        Environment environment = mock(Environment.class);
        when(repository.findById(SystemSettingService.TEAMS_WEBHOOK_URL)).thenReturn(Optional.empty());
        when(repository.findById(SystemSettingService.WEBHOOK_URL)).thenReturn(Optional.empty());
        when(environment.getProperty(SystemSettingService.TEAMS_WEBHOOK_URL)).thenReturn(null);
        when(environment.getProperty("REMINDER_TEAMS_WEBHOOK_URL")).thenReturn("https://teams.example.com/hook/secret");
        when(environment.getProperty("spring.mail.host", "")).thenReturn("smtp.company.com");

        Map<String, Object> status = new SystemSettingService(repository, currentUser, environment)
                .reminderEndpointStatus();

        assertEquals(true, status.get("teamsWebhookConfigured"));
        assertEquals("ENVIRONMENT", status.get("teamsWebhookSource"));
        assertEquals(true, status.get("smtpConfigured"));
        assertEquals("smtp.company.com", status.get("smtpHost"));
    }

    @Test
    void validatesEndpointBeforeSaving() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        Environment environment = mock(Environment.class);
        SystemSettingService service = new SystemSettingService(repository, currentUser, environment);

        assertThrows(IllegalArgumentException.class,
                () -> service.updateEndpoints(Map.of(SystemSettingService.WEBHOOK_URL, "not-a-url")));
        verify(repository, never()).save(any());

        service.updateEndpoints(Map.of(SystemSettingService.WEBHOOK_URL, "https://api.company.com/reminders"));
        verify(repository).save(argThat(setting -> SystemSettingService.WEBHOOK_URL.equals(setting.getKey())
                && "https://api.company.com/reminders".equals(setting.getValue())));
    }
}
