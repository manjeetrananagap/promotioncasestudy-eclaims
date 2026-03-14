package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Partner Service → workshop.assigned
 * Consumed by: Notification (confirm appointment to customer), Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WorkshopAssignedEvent {

    private String        eventId;
    private String        eventType = "WORKSHOP_ASSIGNED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderEmail;
    private String        policyHolderPhone;

    private UUID          workshopId;
    private String        workshopName;
    private String        workshopAddress;
    private String        workshopPhone;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime appointmentDateTime;
    private String        estimatedCompletionDays;
}
