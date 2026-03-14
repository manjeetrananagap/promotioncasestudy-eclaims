package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Payment Service → payment.processed
 * Consumed by: Workflow (close BPMN), Notification (send receipt), Document, Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentProcessedEvent {

    private String        eventId;
    private String        eventType = "PAYMENT_PROCESSED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderEmail;
    private String        policyHolderPhone;
    private BigDecimal    customerAmountPaid;
    private BigDecimal    insurerAmountPaid;
    private String        transactionReference; // Stripe payment intent ID
    private String        paymentMethod;         // CARD, UPI, NET_BANKING
}
