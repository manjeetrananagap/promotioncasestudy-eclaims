package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Partner Service → surveyor.assigned
 * Consumed by: Notification (alert customer + surveyor), Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SurveyorAssignedEvent {

    private String        eventId;
    private String        eventType = "SURVEYOR_ASSIGNED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderEmail;
    private String        policyHolderPhone;

    // Surveyor details
    private UUID          surveyorId;
    private String        surveyorName;
    private String        surveyorEmail;
    private String        surveyorPhone;
    private String        estimatedArrival;   // Human-readable ETA e.g. "within 2 hours"
    private String        mapsNavigationLink; // Deep-link to Maps with accident coords
}
