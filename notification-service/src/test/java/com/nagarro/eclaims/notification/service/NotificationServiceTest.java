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
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link NotificationService} — email sending and idempotency.
 * No Spring context or real SMTP server required.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private JavaMailSender              mailSender;
    @Mock private NotificationLogRepository  logRepository;

    @InjectMocks private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "fromAddress", "noreply@eclaims.test");
        ReflectionTestUtils.setField(notificationService, "fromName", "eClaims Test");
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendEmail: sends mail and logs SENT when no previous record")
    void sendEmail_sendsAndLogs() {
        UUID claimId = UUID.randomUUID();
        when(logRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(logRepository.save(any())).thenReturn(new NotificationLog());

        notificationService.sendEmail("evt-001", claimId, "claim.submitted",
                "customer@test.com", "Claim Received", "Your claim has been received.");

        // Verify mail was sent
        ArgumentCaptor<SimpleMailMessage> msgCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(msgCaptor.capture());
        assertThat(msgCaptor.getValue().getTo()).containsExactly("customer@test.com");
        assertThat(msgCaptor.getValue().getSubject()).isEqualTo("Claim Received");

        // Verify audit log saved with SENT status
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SENT");
        assertThat(logCaptor.getValue().getIdempotencyKey()).isEqualTo("evt-001");
    }

    // ── Idempotency ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("sendEmail: skips send when idempotency key already exists (duplicate Kafka event)")
    void sendEmail_idempotent_skipsOnDuplicate() {
        when(logRepository.existsByIdempotencyKey("evt-dup")).thenReturn(true);

        notificationService.sendEmail("evt-dup", UUID.randomUUID(), "claim.submitted",
                "customer@test.com", "Subject", "Body");

        verifyNoInteractions(mailSender);
        verify(logRepository, never()).save(any());
    }

    // ── Missing email address ─────────────────────────────────────────────────

    @Test
    @DisplayName("sendEmail: logs SKIPPED when toEmail is null")
    void sendEmail_skipped_whenEmailNull() {
        when(logRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(logRepository.save(any())).thenReturn(new NotificationLog());

        notificationService.sendEmail("evt-002", UUID.randomUUID(), "claim.submitted",
                null, "Subject", "Body");

        verifyNoInteractions(mailSender);
        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("SKIPPED");
    }

    @Test
    @DisplayName("sendEmail: logs SKIPPED when toEmail is blank")
    void sendEmail_skipped_whenEmailBlank() {
        when(logRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        when(logRepository.save(any())).thenReturn(new NotificationLog());

        notificationService.sendEmail("evt-003", UUID.randomUUID(), "claim.submitted",
                "  ", "Subject", "Body");

        verifyNoInteractions(mailSender);
    }

    // ── Mail send failure ─────────────────────────────────────────────────────

    @Test
    @DisplayName("sendEmail: logs FAILED (no exception thrown) when mail sender throws")
    void sendEmail_logsFailedOnMailException() {
        when(logRepository.existsByIdempotencyKey(anyString())).thenReturn(false);
        doThrow(new org.springframework.mail.MailSendException("SMTP error"))
                .when(mailSender).send(any(SimpleMailMessage.class));
        when(logRepository.save(any())).thenReturn(new NotificationLog());

        // Should NOT propagate the exception to the caller
        assertThatCode(() ->
                notificationService.sendEmail("evt-004", UUID.randomUUID(), "claim.submitted",
                        "bad@test.com", "Subject", "Body")
        ).doesNotThrowAnyException();

        ArgumentCaptor<NotificationLog> logCaptor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(logRepository).save(logCaptor.capture());
        assertThat(logCaptor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(logCaptor.getValue().getErrorMessage()).contains("SMTP error");
    }
}
