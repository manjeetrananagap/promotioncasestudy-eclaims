package com.nagarro.eclaims.partner.consumer;

import com.nagarro.eclaims.events.AssessmentSubmittedEvent;
import com.nagarro.eclaims.events.ClaimValidatedEvent;
import com.nagarro.eclaims.partner.service.PartnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for Partner Service.
 *
 * claim.validated    → triggers geo-radius surveyor auto-assignment
 * assessment.submitted → releases surveyor back to AVAILABLE pool
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimEventConsumer {

    private final PartnerService partnerService;

    /** Triggered when claim is validated — auto-assign nearest available surveyor */
    @KafkaListener(topics = "claim.validated", groupId = "partner-service-group")
    public void onClaimValidated(ClaimValidatedEvent event) {
        log.info("Consumed claim.validated — claimId:{} claim:{}", event.getClaimId(), event.getClaimNumber());
        partnerService.assignSurveyor(event);
    }

    /** Release surveyor back to available when assessment is submitted */
    @KafkaListener(topics = "assessment.submitted", groupId = "partner-service-group")
    public void onAssessmentSubmitted(AssessmentSubmittedEvent event) {
        log.info("Consumed assessment.submitted — releasing surveyor:{}", event.getSurveyorId());
        if (event.getSurveyorId() != null) {
            partnerService.releaseSurveyor(event.getSurveyorId());
        }
    }
}
