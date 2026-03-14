package com.nagarro.eclaims.notification.service;

import com.nagarro.eclaims.notification.entity.NotificationLog;
import com.nagarro.eclaims.notification.repository.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Sends email notifications and logs every attempt to notification_db.
 *
 * Idempotency: each Kafka event has a unique eventId.
 * Before sending, we check if that eventId was already processed.
 * This prevents duplicate emails when Kafka redelivers a message.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final JavaMailSender mailSender;
    private final NotificationLogRepository logRepository;

    @Value("${spring.eclaims.mail.from:noreply@eclaims.yourdomain.com}")
    private String fromAddress;

    @Value("${spring.eclaims.mail.from-name:eClaims}")
    private String fromName;

    /**
     * Sends an email and records it in the audit log.
     *
     * @param idempotencyKey  unique event ID from Kafka — prevents duplicate sends
     * @param claimId         claim this notification relates to
     * @param eventType       Kafka topic name (for audit log)
     * @param toEmail         recipient email address
     * @param subject         email subject line
     * @param body            email body text
     */
    @Transactional
    public void sendEmail(String idempotencyKey, UUID claimId, String eventType,
                           String toEmail, String subject, String body) {

        // Guard: skip if already processed (Kafka at-least-once redelivery)
        if (logRepository.existsByIdempotencyKey(idempotencyKey)) {
            log.info("Skipping duplicate notification — key:{} claimId:{}", idempotencyKey, claimId);
            return;
        }

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No recipient email for claimId:{} event:{}", claimId, eventType);
            logNotification(idempotencyKey, claimId, eventType, null, "EMAIL",
                    subject, body, "SKIPPED", "No recipient email");
            return;
        }

        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(fromName + " <" + fromAddress + ">");
            msg.setTo(toEmail);
            msg.setSubject(subject);
            msg.setText(body);
            mailSender.send(msg);

            logNotification(idempotencyKey, claimId, eventType, toEmail,
                    "EMAIL", subject, body, "SENT", null);

            log.info("Email sent — to:{} subject:{} claimId:{}", toEmail, subject, claimId);

        } catch (Exception ex) {
            log.error("Failed to send email to:{} — {}", toEmail, ex.getMessage());
            logNotification(idempotencyKey, claimId, eventType, toEmail,
                    "EMAIL", subject, body, "FAILED", ex.getMessage());
        }
    }

    private void logNotification(String idempotencyKey, UUID claimId, String eventType,
                                   String email, String channel, String subject, String body,
                                   String status, String error) {
        logRepository.save(NotificationLog.builder()
                .idempotencyKey(idempotencyKey)
                .claimId(claimId)
                .eventType(eventType)
                .recipientEmail(email)
                .channel(channel)
                .subject(subject)
                .body(body)
                .status(status)
                .errorMessage(error)
                .build());
    }
}
