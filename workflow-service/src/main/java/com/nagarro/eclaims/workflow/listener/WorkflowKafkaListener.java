package com.nagarro.eclaims.workflow.listener;

import com.nagarro.eclaims.events.*;
import io.camunda.zeebe.client.ZeebeClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumes Kafka events and drives the Camunda 8 BPMN workflow.
 *
 * claim.validated  → starts process instance
 * assessment.submitted → correlates message to advance BPMN
 * repair.completed → correlates message to trigger payment step
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowKafkaListener {

    private final ZeebeClient zeebeClient;

    /** When claim is validated, start BPMN process instance. */
    @KafkaListener(topics = "claim.validated",
                   groupId = "workflow-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void onClaimValidated(
            @Payload ClaimValidatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition) {

        log.info("Starting BPMN process for claim: {} (partition={})",
                 event.getClaimId(), partition);

        zeebeClient.newCreateInstanceCommand()
            .bpmnProcessId("eclaims-claim-lifecycle")
            .latestVersion()
            .variables(Map.of(
                "claimId",      event.getClaimId().toString(),
                "claimNumber",  event.getClaimNumber(),
                "policyId",     event.getPolicyId(),
                "accidentLat",  event.getAccidentLat() != null ? event.getAccidentLat() : 0.0,
                "accidentLng",  event.getAccidentLng() != null ? event.getAccidentLng() : 0.0,
                "assessmentValid", false,
                "approved",     false
            ))
            .send()
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to start process for claim {}: {}", event.getClaimId(), ex.getMessage());
                } else {
                    log.info("BPMN process started: processInstanceKey={} for claim={}",
                             result.getProcessInstanceKey(), event.getClaimId());
                }
            });
    }

    /** When assessment is submitted, correlate message to advance to adjustor review. */
    @KafkaListener(topics = "assessment.submitted",
                   groupId = "workflow-service-group")
    public void onAssessmentSubmitted(@Payload AssessmentSubmittedEvent event) {
        log.info("Assessment submitted for claim: {}", event.getClaimId());

        zeebeClient.newPublishMessageCommand()
            .messageName("assessmentSubmitted")
            .correlationKey(event.getClaimId().toString())
            .variables(Map.of(
                "assessmentValid", true,
                "surveyorId",      event.getSurveyorId() != null ? event.getSurveyorId() : "",
                "estimatedAmount", event.getEstimatedRepairAmount() != null
                                   ? event.getEstimatedRepairAmount().toString() : "0"
            ))
            .send()
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to correlate assessment: {}", ex.getMessage());
                else log.info("Assessment correlated for claim: {}", event.getClaimId());
            });
    }

    /** When repair is completed, correlate to trigger payment step. */
    @KafkaListener(topics = "repair.completed",
                   groupId = "workflow-service-group")
    public void onRepairCompleted(@Payload RepairCompletedEvent event) {
        log.info("Repair completed for claim: {}", event.getClaimId());

        zeebeClient.newPublishMessageCommand()
            .messageName("repairCompleted")
            .correlationKey(event.getClaimId().toString())
            .variables(Map.of(
                "finalRepairCost", event.getFinalRepairCost() != null
                                   ? event.getFinalRepairCost().toString() : "0",
                "repairCompletedAt", event.getOccurredAt() != null
                                     ? event.getOccurredAt().toString() : ""
            ))
            .send()
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("Failed to correlate repair: {}", ex.getMessage());
                else log.info("Repair correlated for claim: {}", event.getClaimId());
            });
    }
}
