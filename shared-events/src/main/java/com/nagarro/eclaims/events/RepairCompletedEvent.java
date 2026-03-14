package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Partner Service → repair.completed
 * Consumed by: Payment Service (trigger payment collection), Notification, Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RepairCompletedEvent {

    private String        eventId;
    private String        eventType = "REPAIR_COMPLETED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderEmail;
    private String        policyHolderPhone;
    private UUID          workshopId;
    private String        workshopName;
    private BigDecimal    finalRepairCost;
    private BigDecimal    customerPayableAmount;
    private BigDecimal    insurerPayableAmount;
}
