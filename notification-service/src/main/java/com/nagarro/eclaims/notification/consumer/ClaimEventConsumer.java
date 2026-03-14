package com.nagarro.eclaims.notification.consumer;

import com.nagarro.eclaims.events.*;
import com.nagarro.eclaims.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for all eClaims events that require customer/surveyor notification.
 *
 * Each listener method:
 * 1. Receives the event from Kafka
 * 2. Builds a human-friendly email subject + body
 * 3. Delegates to NotificationService which handles idempotency + sending + logging
 *
 * Consumer group: notification-service-group
 * If this service is down, Kafka retains messages — they're processed when it restarts.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimEventConsumer {

    private final NotificationService notificationService;

    /** claim.submitted — sent to customer confirming receipt */
    @KafkaListener(topics = "claim.submitted", groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onClaimSubmitted(ClaimSubmittedEvent event) {
        log.debug("Consumed claim.submitted — claimId:{}", event.getClaimId());

        notificationService.sendEmail(
            event.getEventId(),
            event.getClaimId(),
            "claim.submitted",
            event.getPolicyHolderEmail(),
            "Claim Received — " + event.getClaimNumber(),
            buildClaimSubmittedBody(event)
        );
    }

    /** surveyor.assigned — sent to customer with surveyor ETA */
    @KafkaListener(topics = "surveyor.assigned", groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onSurveyorAssigned(SurveyorAssignedEvent event) {
        log.debug("Consumed surveyor.assigned — claimId:{}", event.getClaimId());

        notificationService.sendEmail(
            event.getEventId(),
            event.getClaimId(),
            "surveyor.assigned",
            event.getPolicyHolderEmail(),
            "Surveyor Assigned — " + event.getClaimNumber(),
            buildSurveyorAssignedBody(event)
        );
    }

    /** claim.approved — sent to customer with approved amount */
    @KafkaListener(topics = "claim.approved", groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onClaimApproved(ClaimApprovedEvent event) {
        log.debug("Consumed claim.approved — claimId:{}", event.getClaimId());

        notificationService.sendEmail(
            event.getEventId(),
            event.getClaimId(),
            "claim.approved",
            event.getPolicyHolderEmail(),
            "Claim Approved — " + event.getClaimNumber(),
            buildClaimApprovedBody(event)
        );
    }

    /** workshop.assigned — customer appointment confirmation */
    @KafkaListener(topics = "workshop.assigned", groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onWorkshopAssigned(WorkshopAssignedEvent event) {
        log.debug("Consumed workshop.assigned — claimId:{}", event.getClaimId());

        notificationService.sendEmail(
            event.getEventId(),
            event.getClaimId(),
            "workshop.assigned",
            event.getPolicyHolderEmail(),
            "Workshop Appointment Confirmed — " + event.getClaimNumber(),
            buildWorkshopAssignedBody(event)
        );
    }

    /** repair.status.updated — milestone progress to customer */
    @KafkaListener(topics = "repair.status.updated", groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onRepairStatusUpdated(RepairStatusUpdatedEvent event) {
        log.debug("Consumed repair.status.updated — claimId:{} milestone:{}", event.getClaimId(), event.getMilestone());

        notificationService.sendEmail(
            event.getEventId(),
            event.getClaimId(),
            "repair.status.updated",
            event.getPolicyHolderEmail(),
            "Repair Update: " + event.getMilestoneLabel() + " — " + event.getClaimNumber(),
            buildRepairUpdateBody(event)
        );
    }

    /** claim.closed — closure summary + receipt */
    @KafkaListener(topics = "claim.closed", groupId = "notification-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onClaimClosed(ClaimClosedEvent event) {
        log.debug("Consumed claim.closed — claimId:{}", event.getClaimId());

        notificationService.sendEmail(
            event.getEventId(),
            event.getClaimId(),
            "claim.closed",
            event.getPolicyHolderEmail(),
            "Claim Closed — " + event.getClaimNumber(),
            buildClaimClosedBody(event)
        );
    }

    // ── Email body builders ───────────────────────────────────────────────────

    private String buildClaimSubmittedBody(ClaimSubmittedEvent e) {
        return """
            Dear %s,

            Your insurance claim has been successfully submitted.

            Claim Number : %s
            Vehicle      : %s %s (%s)
            Incident Date: %s
            Location     : %s

            Our team will review your claim shortly. You will receive an update
            once a surveyor has been assigned.

            Track your claim at: http://localhost:3000/claims/%s

            Regards,
            eClaims Team | Nagarro
            """.formatted(
                e.getPolicyHolderName(), e.getClaimNumber(),
                e.getVehicleMake(), e.getVehicleModel(), e.getVehicleReg(),
                e.getIncidentDate(), e.getAccidentAddress(),
                e.getClaimId()
            );
    }

    private String buildSurveyorAssignedBody(SurveyorAssignedEvent e) {
        return """
            Dear Customer,

            A surveyor has been assigned to inspect your vehicle.

            Claim Number   : %s
            Surveyor Name  : %s
            Surveyor Phone : %s
            Estimated ETA  : %s

            Navigation to accident site: %s

            The surveyor will contact you before visiting.

            Regards,
            eClaims Team | Nagarro
            """.formatted(
                e.getClaimNumber(), e.getSurveyorName(),
                e.getSurveyorPhone(), e.getEstimatedArrival(),
                e.getMapsNavigationLink()
            );
    }

    private String buildClaimApprovedBody(ClaimApprovedEvent e) {
        return """
            Dear %s,

            Your insurance claim has been APPROVED.

            Claim Number       : %s
            Approved Amount    : ₹%s
            Insurer Pays       : ₹%s
            Your Contribution  : ₹%s

            Next Step: Log in to select a repair workshop near you.
            Portal: http://localhost:3000/claims/%s/workshops

            Regards,
            eClaims Team | Nagarro
            """.formatted(
                e.getPolicyHolderName(), e.getClaimNumber(),
                e.getApprovedAmount(), e.getInsurerContribution(),
                e.getCustomerContribution(), e.getClaimId()
            );
    }

    private String buildWorkshopAssignedBody(WorkshopAssignedEvent e) {
        return """
            Dear Customer,

            Your vehicle repair appointment has been confirmed.

            Claim Number       : %s
            Workshop           : %s
            Address            : %s
            Phone              : %s
            Appointment        : %s
            Est. Completion    : %s days

            Please bring your vehicle at the scheduled time.

            Regards,
            eClaims Team | Nagarro
            """.formatted(
                e.getClaimNumber(), e.getWorkshopName(), e.getWorkshopAddress(),
                e.getWorkshopPhone(), e.getAppointmentDateTime(),
                e.getEstimatedCompletionDays()
            );
    }

    private String buildRepairUpdateBody(RepairStatusUpdatedEvent e) {
        return """
            Dear Customer,

            Repair update for Claim %s at %s:

            Status: %s
            Estimated Completion: %s

            You will be notified when the next milestone is reached.

            Regards,
            eClaims Team | Nagarro
            """.formatted(
                e.getClaimNumber(), e.getWorkshopName(),
                e.getMilestoneLabel(), e.getEstimatedCompletionDate()
            );
    }

    private String buildClaimClosedBody(ClaimClosedEvent e) {
        return """
            Dear %s,

            Your insurance claim has been CLOSED.

            Claim Number : %s
            Closed By    : %s
            Reason       : %s

            Thank you for choosing eClaims. If you have any questions,
            please contact support@eclaims.yourdomain.com.

            Regards,
            eClaims Team | Nagarro
            """.formatted(
                e.getPolicyHolderName(), e.getClaimNumber(),
                e.getClosedBy(), e.getClosureReason()
            );
    }
}
