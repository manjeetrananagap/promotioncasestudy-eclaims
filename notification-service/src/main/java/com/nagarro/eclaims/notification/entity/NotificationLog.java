package com.nagarro.eclaims.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
import java.util.UUID;

/** JPA entity — audit log of every notification sent. */
@Entity
@Table(name = "notification_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_id")
    private UUID claimId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "recipient_email", length = 200)
    private String recipientEmail;

    @Column(name = "recipient_phone", length = 30)
    private String recipientPhone;

    @Column(name = "channel", nullable = false, length = 20)
    private String channel;  // EMAIL | SMS

    @Column(name = "subject", length = 300)
    private String subject;

    @Column(name = "body")
    private String body;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "SENT";

    /** Event ID from Kafka payload — used as idempotency key. UNIQUE constraint in DB. */
    @Column(name = "idempotency_key", unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "sent_at", updatable = false)
    private LocalDateTime sentAt;
}
