package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Claims Service → assessment.submitted
 * Consumed by: Workflow (advance to AdjustorReview), Notification, Document, Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AssessmentSubmittedEvent {

    private String        eventId;
    private String        eventType = "ASSESSMENT_SUBMITTED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderEmail;
    private String        policyHolderPhone;
    private UUID          surveyorId;
    private String        surveyorName;
    private BigDecimal    estimatedRepairAmount;
    private String        damageDescription;
    private String        assessmentDocumentKey; // MinIO object key
}
