package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Claims Service → claim.submitted
 * Consumed by: Workflow, Notification, Document, Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimSubmittedEvent {

    private String        eventId;          // UUID for idempotency
    private String        eventType = "CLAIM_SUBMITTED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyId;
    private String        policyHolderName;
    private String        policyHolderEmail;
    private String        policyHolderPhone;
    private String        vehicleReg;
    private String        vehicleMake;
    private String        vehicleModel;

    /** GPS coords from mobile — used for geo-radius surveyor matching */
    private BigDecimal    accidentLat;
    private BigDecimal    accidentLng;
    private String        accidentAddress;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate     incidentDate;
    private String        incidentDescription;
    private String        submittedByUserId;
}
