package com.nagarro.eclaims.notification.repository;

import com.nagarro.eclaims.notification.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {
    /** Check if this event was already processed (idempotency guard) */
    boolean existsByIdempotencyKey(String idempotencyKey);
}
