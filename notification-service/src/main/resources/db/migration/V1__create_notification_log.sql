-- V1__create_notification_log.sql
-- Notification audit log — every sent notification is recorded.
-- idempotency_key prevents duplicate sends when Kafka redelivers a message.

CREATE TABLE notification_log (
    id               UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id         UUID,
    event_type       VARCHAR(50)  NOT NULL,  -- Kafka topic/event name
    recipient_email  VARCHAR(200),
    recipient_phone  VARCHAR(30),
    channel          VARCHAR(20)  NOT NULL,  -- EMAIL | SMS | PUSH
    subject          VARCHAR(300),
    body             TEXT,
    status           VARCHAR(20)  NOT NULL DEFAULT 'SENT',  -- SENT | FAILED | SKIPPED
    -- Idempotency: eventId from Kafka payload. UNIQUE prevents duplicate sends.
    idempotency_key  VARCHAR(100) UNIQUE,
    error_message    TEXT,
    sent_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notif_claim     ON notification_log(claim_id);
CREATE INDEX idx_notif_event     ON notification_log(event_type);
CREATE INDEX idx_notif_sent_at   ON notification_log(sent_at DESC);
