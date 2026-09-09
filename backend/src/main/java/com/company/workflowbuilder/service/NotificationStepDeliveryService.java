package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.runtime.WorkflowInstance;
import com.company.workflowbuilder.entity.user.User;
import com.company.workflowbuilder.entity.workflow.WorkflowStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationStepDeliveryService {
    private final ObjectProvider<JavaMailSender> mailSenders;
    private final SystemSettingService settings;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().build();

    public void deliver(Set<String> channels, Collection<User> recipients, String title, String body,
            String webhookUrl, WorkflowInstance instance, WorkflowStep step) {
        for (String channel : channels) {
            if ("IN_APP".equals(channel))
                continue;
            try {
                switch (channel) {
                    case "EMAIL" -> email(recipients, title, body);
                    case "TEAMS" -> post(required("reminder.teams.webhookUrl"), mapper.writeValueAsString(Map.of(
                            "text", title + "\n\n" + body)));
                    case "WEBHOOK" -> post(webhookUrl, mapper.writeValueAsString(Map.of(
                            "event", "WORKFLOW_NOTIFICATION", "requestCode", instance.getRequestCode(),
                            "workflowId", instance.getWorkflow().getId(), "workflowName",
                            instance.getWorkflow().getName(),
                            "stepId", step.getId(), "stepName", step.getLabel(), "title", title, "body", body)));
                    default -> log.warn("Unsupported Notification Step channel: {}", channel);
                }
            } catch (Exception e) {
                log.warn("Notification Step {} failed to send channel {}: {}", step.getId(), channel, e.getMessage());
            }
        }
    }

    private void email(Collection<User> recipients, String title, String body) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null)
            throw new IllegalStateException("SMTP is not configured");
        for (User recipient : recipients) {
            if (recipient.getEmail() == null || recipient.getEmail().isBlank())
                continue;
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(recipient.getEmail());
            message.setSubject(title);
            message.setText(body);
            sender.send(message);
        }
    }

    private void post(String url, String body) throws Exception {
        if (url == null || url.isBlank())
            throw new IllegalStateException("Webhook endpoint is not configured");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Endpoint returned HTTP " + response.statusCode());
    }

    private String required(String key) {
        String value = settings.value(key);
        if (value == null || value.isBlank())
            throw new IllegalStateException("Missing system setting: " + key);
        return value;
    }
}
