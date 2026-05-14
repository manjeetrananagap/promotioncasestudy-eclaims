package com.nagarro.eclaims.notification.service;

import com.nagarro.eclaims.notification.entity.NotificationLog;
import com.nagarro.eclaims.notification.repository.NotificationLogRepository;
import com.twilio.Twilio;
import com.twilio.rest.api.v2010.account.Message;
import com.twilio.type.PhoneNumber;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Sends SMS notifications via Twilio and logs every attempt to notification_db.
 *
 * Idempotency: each Kafka event has a unique eventId.
 * Before sending, we check if that eventId was already processed (for SMS channel).
 *
 * Twilio credentials are injected from environment variables / GCP Secret Manager.
 * Set TWILIO_ENABLED=false in local dev to skip actual API calls.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SmsService {

    private final NotificationLogRepository logRepository;

    @Value("${eclaims.twilio.account-sid:}")
    private String accountSid;

    @Value("${eclaims.twilio.auth-token:}")
    private String authToken;

    @Value("${eclaims.twilio.from-number:}")
    private String fromNumber;

    @Value("${eclaims.twilio.enabled:false}")
    private boolean twilioEnabled;

    @PostConstruct
    public void init() {
        if (twilioEnabled && !accountSid.isBlank() && !authToken.isBlank()) {
            Twilio.init(accountSid, authToken);
            log.info("Twilio SDK initialised — from:{}", fromNumber);
        } else {
            log.info("Twilio disabled or credentials not configured — SMS sending skipped");
        }
    }

    /**
     * Sends an SMS and records it in the audit log.
     *
     * @param idempotencyKey  unique event ID from Kafka — prevents duplicate sends
     * @param claimId         claim this notification relates to
     * @param eventType       Kafka topic name (for audit log)
     * @param toPhone         recipient phone number in E.164 format (+1234567890)
     * @param body            SMS body text (max 160 chars for single segment)
     */
    @Transactional
    public void sendSms(String idempotencyKey, UUID claimId, String eventType,
                        String toPhone, String body) {

        String smsKey = idempotencyKey + ":SMS";

        // Guard: skip if already processed
        if (logRepository.existsByIdempotencyKey(smsKey)) {
            log.info("Skipping duplicate SMS — key:{} claimId:{}", smsKey, claimId);
            return;
        }

        if (toPhone == null || toPhone.isBlank()) {
            log.warn("No phone number for claimId:{} event:{}", claimId, eventType);
            logSms(smsKey, claimId, eventType, null, body, "SKIPPED", "No phone number");
            return;
        }

        if (!twilioEnabled) {
            log.info("[DEV] SMS skipped (Twilio disabled) — to:{} body:{}", toPhone, body);
            logSms(smsKey, claimId, eventType, toPhone, body, "SKIPPED_DEV", null);
            return;
        }

        try {
            Message message = Message.creator(
                    new PhoneNumber(toPhone),
                    new PhoneNumber(fromNumber),
                    body
            ).create();

            logSms(smsKey, claimId, eventType, toPhone, body, "SENT", null);
            log.info("SMS sent — to:{} sid:{} claimId:{}", toPhone, message.getSid(), claimId);

        } catch (Exception ex) {
            log.error("Failed to send SMS to:{} — {}", toPhone, ex.getMessage());
            logSms(smsKey, claimId, eventType, toPhone, body, "FAILED", ex.getMessage());
        }
    }

    private void logSms(String idempotencyKey, UUID claimId, String eventType,
                        String phone, String body, String status, String error) {
        logRepository.save(NotificationLog.builder()
                .idempotencyKey(idempotencyKey)
                .claimId(claimId)
                .eventType(eventType)
                .recipientEmail(phone)   // reusing field for phone in SMS log rows
                .channel("SMS")
                .subject("SMS Notification")
                .body(body)
                .status(status)
                .errorMessage(error)
                .build());
    }
}
