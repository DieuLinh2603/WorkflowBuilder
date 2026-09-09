package com.company.workflowbuilder.service;

import com.company.workflowbuilder.entity.notification.ReminderDelivery;
import com.company.workflowbuilder.entity.runtime.TaskStatus;
import com.company.workflowbuilder.repository.ReminderDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExternalReminderDeliveryService {
  private final ReminderDeliveryRepository deliveries;
  private final SystemSettingService settings;
  private final ObjectProvider<JavaMailSender> mailSenders;
  private final HttpClient http = HttpClient.newBuilder().build();

  @Scheduled(fixedDelayString = "${app.workflow.delivery-worker-ms:60000}")
  @SchedulerLock(name = "externalReminderDelivery", lockAtMostFor = "PT50S")
  @Transactional
  public void deliver() {
    for (ReminderDelivery delivery : deliveries
        .findTop100ByStatusInAndNextAttemptAtBeforeOrderByCreatedAtAsc(List.of("QUEUED", "RETRY"), LocalDateTime.now())) {
      if (delivery.getTask().getStatus() != TaskStatus.PENDING) {
        delivery.setStatus("CANCELLED");
        deliveries.save(delivery);
        continue;
      }
      send(delivery);
    }
  }

  private void send(ReminderDelivery d) {
    try {
      switch (d.getChannel()) {
        case "EMAIL" -> email(d);
        case "TEAMS" -> webhook(d, required("reminder.teams.webhookUrl"));
        case "WEBHOOK" -> webhook(d, required("reminder.webhook.url"));
        default -> throw new IllegalArgumentException("Unsupported reminder channel: " + d.getChannel());
      }
      d.setStatus("SENT");
      d.setErrorMessage(null);
    } catch (Exception e) {
      int attempts = d.getAttemptCount() + 1;
      d.setAttemptCount(attempts);
      d.setErrorMessage(e.getMessage());
      d.setStatus(attempts >= 3 ? "FAILED" : "RETRY");
      d.setNextAttemptAt(LocalDateTime.now().plusMinutes(5L * attempts));
      log.warn("Reminder delivery {} failed: {}", d.getId(), e.getMessage());
    }
    deliveries.save(d);
  }

  private void email(ReminderDelivery d) {
    JavaMailSender mailSender = mailSenders.getIfAvailable();
    if (mailSender == null)
      throw new IllegalStateException("SMTP is not configured");
    SimpleMailMessage m = new SimpleMailMessage();
    m.setTo(d.getTask().getAssignee().getEmail());
    m.setSubject("Task sắp tới hạn: " + d.getTask().getInstance().getRequestCode());
    m.setText("Task '" + d.getTask().getStep().getLabel() + "' có hạn " + d.getDeadlineAt());
    mailSender.send(m);
  }

  private void webhook(ReminderDelivery d, String url) throws Exception {
    String body = "{\"requestCode\":\"" + d.getTask().getInstance().getRequestCode() + "\",\"deadline\":\""
        + d.getDeadlineAt() + "\"}";
    HttpRequest req = HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build();
    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
    if (res.statusCode() < 200 || res.statusCode() >= 300)
      throw new IllegalStateException("Webhook returned HTTP " + res.statusCode());
  }

  private String required(String key) {
    String value = settings.value(key);
    if (value == null || value.isBlank())
      throw new IllegalStateException("Missing system setting: " + key);
    return value;
  }
}
