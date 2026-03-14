package com.nagarro.eclaims.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * eClaims — Notification Service (port 8082)
 *
 * Pure Kafka consumer — stateless.
 * Sends emails via SMTP (Mailhog in local dev).
 * All notifications logged to notification_db for audit.
 */
@SpringBootApplication
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
