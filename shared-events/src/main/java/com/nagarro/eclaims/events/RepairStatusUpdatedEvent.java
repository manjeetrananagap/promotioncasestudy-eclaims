package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Partner Service → repair.status.updated
 * Consumed by: Notification (SMS milestone to customer), Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RepairStatusUpdatedEvent {

    private String        eventId;
    private String        eventType = "REPAIR_STATUS_UPDATED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderEmail;
    private String        policyHolderPhone;
    private String        workshopName;

    /**
     * Milestone values:
     * VEHICLE_RECEIVED → DISASSEMBLY → PARTS_ORDERED →
     * REPAIR_IN_PROGRESS → QUALITY_CHECK → READY_FOR_PICKUP
     */
    private String        milestone;
    private String        milestoneLabel;       // Human-readable
    private String        estimatedCompletionDate;
}
