package com.nagarro.eclaims.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Published by Claims Service → claim.closed
 * Consumed by: Document (archive dossier), Notification (closure email), Reporting
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ClaimClosedEvent {

    private String        eventId;
    private String        eventType = "CLAIM_CLOSED";
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt;

    private UUID          claimId;
    private String        claimNumber;
    private String        policyHolderName;
    private String        policyHolderEmail;
    private String        closedBy;     // SYSTEM or username
    private String        closureReason;
}
