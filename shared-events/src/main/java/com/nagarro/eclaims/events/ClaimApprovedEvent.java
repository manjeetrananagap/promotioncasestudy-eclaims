package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Claims Service → claim.approved
 * Consumed by: Partner Service (start workshop geo-search), Notification, Document, Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimApprovedEvent {

    private String        eventId;
    private String        eventType = "CLAIM_APPROVED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderName;
    private String        policyHolderEmail;
    private String        policyHolderPhone;
    private String        vehicleReg;
    private String        vehicleMake;
    private String        vehicleModel;

    private BigDecimal    approvedAmount;
    private BigDecimal    deductibleAmount;
    private BigDecimal    insurerContribution;
    private BigDecimal    customerContribution;

    private String        adjustorName;

    /** Accident coords — used by Partner Service for workshop geo-radius search */
    private BigDecimal    accidentLat;
    private BigDecimal    accidentLng;
    private String        accidentAddress;
}
