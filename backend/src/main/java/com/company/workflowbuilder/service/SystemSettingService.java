package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.SystemSetting;
import com.company.workflowbuilder.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SystemSettingService {
    public static final String DEADLINE_LEAD_HOURS = "deadlineReminderLeadTimeHours";
    public static final String TEAMS_WEBHOOK_URL = "reminder.teams.webhookUrl";
    public static final String WEBHOOK_URL = "reminder.webhook.url";

    private static final Set<String> ENDPOINT_KEYS = Set.of(TEAMS_WEBHOOK_URL, WEBHOOK_URL);

    private final SystemSettingRepository repository;
    private final CurrentUserService currentUser;
    private final Environment environment;

    @Transactional(readOnly = true)
    public int deadlineLeadHours() {
        return repository.findById(DEADLINE_LEAD_HOURS)
                .map(setting -> Integer.parseInt(setting.getValue()))
                .orElse(24);
    }

    /** Database settings take precedence; environment values are deployment fallbacks. */
    @Transactional(readOnly = true)
    public String value(String key) {
        String stored = storedValue(key);
        return hasText(stored) ? stored : environmentValue(key);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reminderEndpointStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        addEndpointStatus(status, "teamsWebhook", TEAMS_WEBHOOK_URL);
        addEndpointStatus(status, "webhook", WEBHOOK_URL);

        String smtpHost = environment.getProperty("spring.mail.host", "").trim();
        status.put("smtpConfigured", hasText(smtpHost));
        status.put("smtpHost", smtpHost);
        status.put("smtpSource", hasText(smtpHost) ? "ENVIRONMENT" : "NONE");
        return status;
    }

    @Transactional
    public int updateDeadlineLeadHours(int hours) {
        currentUser.requireAdmin();
        if (hours < 1 || hours > 720) {
            throw new IllegalArgumentException("Reminder lead time must be between 1 and 720 hours");
        }
        repository.save(new SystemSetting(DEADLINE_LEAD_HOURS, String.valueOf(hours)));
        return hours;
    }

    @Transactional
    public void updateEndpoints(Map<String, String> endpoints) {
        currentUser.requireAdmin();
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("At least one reminder endpoint is required");
        }
        endpoints.forEach(this::saveEndpoint);
    }

    private void saveEndpoint(String key, String rawValue) {
        if (!ENDPOINT_KEYS.contains(key)) {
            throw new IllegalArgumentException("Unsupported setting key: " + key);
        }
        String value = rawValue == null ? "" : rawValue.trim();
        if (hasText(value)) validateHttpUrl(value);
        repository.save(new SystemSetting(key, value));
    }

    private void addEndpointStatus(Map<String, Object> status, String prefix, String key) {
        String stored = storedValue(key);
        String environmentValue = environmentValue(key);
        String effective = hasText(stored) ? stored : environmentValue;
        status.put(prefix + "Configured", hasText(effective));
        status.put(prefix + "Source", hasText(stored) ? "SETTINGS" : hasText(environmentValue) ? "ENVIRONMENT" : "NONE");
        status.put(prefix + "Display", endpointDisplay(effective));
    }

    private String storedValue(String key) {
        return repository.findById(key).map(SystemSetting::getValue).orElse(null);
    }

    private String environmentValue(String key) {
        String property = environment.getProperty(key);
        if (hasText(property)) return property.trim();
        String explicitName = TEAMS_WEBHOOK_URL.equals(key)
                ? "REMINDER_TEAMS_WEBHOOK_URL"
                : WEBHOOK_URL.equals(key) ? "REMINDER_WEBHOOK_URL" : null;
        return explicitName == null ? null : environment.getProperty(explicitName);
    }

    private void validateHttpUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Reminder endpoint must be a valid HTTP or HTTPS URL");
        }
    }

    private String endpointDisplay(String value) {
        if (!hasText(value)) return "";
        try {
            URI uri = URI.create(value);
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port > 0 ? ":" + port : "") + "/••••••";
        } catch (RuntimeException exception) {
            return "••••••";
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
