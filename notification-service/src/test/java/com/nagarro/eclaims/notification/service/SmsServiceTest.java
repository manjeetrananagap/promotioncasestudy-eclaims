package com.nagarro.eclaims.notification.service;

import com.nagarro.eclaims.notification.entity.NotificationLog;
import com.nagarro.eclaims.notification.repository.NotificationLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SmsService} — Twilio disabled simulation path and idempotency.
 *
 * The Twilio live API path is not tested here — it requires real credentials
 * and belongs in an E2E test suite. All tests run with twilioEnabled=false.
 */
@ExtendWith(MockitoExtension.class)
class SmsServiceTest {

    @Mock private NotificationLogRepository logRepository;

    @InjectMocks private SmsService smsService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(smsService, "twilioEnabled", false);
        ReflectionTestUtils.setField(smsService, "accountSid",   "");
        ReflectionTestUtils.setField(smsService, "authToken",    "");
        ReflectionTestUtils.setField(smsService, "fromNumber",   "+15550000000");
        smsService.init();  // @PostConstruct
    }

    // ── Dev-mode (Twilio disabled) ────────────────────────────────────────────

    @Test
    @DisplayName("sendSms: dev mode logs SKIPPED_DEV when Twilio is disabled")
    void sendSms_devMode_logsSkippedDev() {
        UUID claimId = UUID.randomUUID();
        when(logRepository.existsByIdempotencyKey("evt-sms:SMS")).thenReturn(false);
        when(logRepository.save(any())).thenReturn(new NotificationLog());

        smsService.sendSms("evt-sms", claimId, "claim.submitted",
                "+919876543210", "Your claim has been submitted.");

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SKIPPED_DEV");
        assertThat(logCaptor.getValue().getChannel()).isEqualTo("SMS");
        assertThat(logCaptor.getValue().getIdempotencyKey()).isEqualTo("evt-sms:SMS");
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendSms: skips entirely when SMS idempotency key already processed")
    void sendSms_idempotent_skipsOnDuplicate() {
        // The smsKey is eventId + ":SMS"
        when(logRepository.existsByIdempotencyKey("evt-dup:SMS")).thenReturn(true);

        smsService.sendSms("evt-dup", UUID.randomUUID(), "claim.submitted",
                "+919876543210", "body");

        verify(logRepository, never()).save(any());
    }

    // ── Missing phone number ──────────────────────────────────────────────────

    @Test
    @DisplayName("sendSms: logs SKIPPED when phone is null")
    void sendSms_skipped_whenPhoneNull() {
        when(logRepository.existsByIdempotencyKey("evt-nophone:SMS")).thenReturn(false);
        when(logRepository.save(any())).thenReturn(new NotificationLog());

        smsService.sendSms("evt-nophone", UUID.randomUUID(), "claim.submitted",
                null, "body");

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("sendSms: uses distinct idempotency key from email (appends :SMS suffix)")
    void sendSms_usesDistinctIdempotencyKey() {
        String eventId = "evt-shared";
        // Email key would be just "evt-shared", SMS key is "evt-shared:SMS"
        when(logRepository.existsByIdempotencyKey(eventId + ":SMS")).thenReturn(false);
        when(logRepository.save(any())).thenReturn(new NotificationLog());

        smsService.sendSms(eventId, UUID.randomUUID(), "claim.submitted",
                "+919876543210", "body");

        verify(logRepository).existsByIdempotencyKey(eventId + ":SMS");
    }
}
