package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Claims Service → claim.validated
 * Consumed by: Workflow (starts BPMN), Notification, Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimValidatedEvent {

    private String        eventId;
    private String        eventType = "CLAIM_VALIDATED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyId;
    private String        policyHolderName;
    private String        policyHolderEmail;

    // Coords repeated so Workflow needn't call Claims Service
    private BigDecimal    accidentLat;
    private BigDecimal    accidentLng;
    private String        accidentAddress;
}
