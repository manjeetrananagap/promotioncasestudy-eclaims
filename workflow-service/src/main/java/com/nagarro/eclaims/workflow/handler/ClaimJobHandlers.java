package com.nagarro.eclaims.workflow.handler;

import com.nagarro.eclaims.events.ClaimApprovedEvent;
import com.nagarro.eclaims.events.ClaimClosedEvent;
import io.camunda.zeebe.client.api.response.ActivatedJob;
import io.camunda.zeebe.client.api.worker.JobClient;
import io.camunda.zeebe.spring.client.annotation.JobWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Zeebe Job Workers — implement each BPMN Service Task.
 *
 * Camunda 8 Zeebe calls these when a service task token arrives.
 * Workers complete the job (or throw BPMN errors for rejection paths).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ClaimJobHandlers {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** BPMN task: validate-claim — verifies policy coverage and claim completeness. */
    @JobWorker(type = "validate-claim")
    public void validateClaim(JobClient client, ActivatedJob job) {
        String claimId = (String) job.getVariablesAsMap().get("claimId");
        log.info("[BPMN] validate-claim for claimId={}", claimId);

        // In production: call Claims Service REST to trigger VALIDATED status
        // POC: mark as valid and continue
        client.newCompleteCommand(job.getKey())
            .variables(Map.of("validationPassed", true))
            .send()
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("validate-claim failed: {}", ex.getMessage());
                else log.info("validate-claim completed for claim={}", claimId);
            });
    }

    /** BPMN task: auto-assign-surveyor — geo-radius search via Partner Service. */
    @JobWorker(type = "auto-assign-surveyor")
    public void autoAssignSurveyor(JobClient client, ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String claimId = (String) vars.get("claimId");
        double lat = vars.getOrDefault("accidentLat", 0.0) instanceof Number n ? n.doubleValue() : 0.0;
        double lng = vars.getOrDefault("accidentLng", 0.0) instanceof Number n ? n.doubleValue() : 0.0;

        log.info("[BPMN] auto-assign-surveyor for claim={} at [{},{}]", claimId, lat, lng);

        // In production: call Partner Service to find nearest surveyor
        // POC: simulate assignment
        String surveyorId = "SVR-001";
        String surveyorName = "Rajesh Kumar";
        String surveyorPhone = "+91-9876543210";

        client.newCompleteCommand(job.getKey())
            .variables(Map.of(
                "surveyorId",    surveyorId,
                "surveyorName",  surveyorName,
                "surveyorPhone", surveyorPhone,
                "estimatedEtaHours", 4
            ))
            .send()
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("auto-assign-surveyor failed: {}", ex.getMessage());
                else log.info("Surveyor {} assigned to claim={}", surveyorId, claimId);
            });
    }

    /** BPMN task: assign-workshop — geo-radius workshop selection. */
    @JobWorker(type = "assign-workshop")
    public void assignWorkshop(JobClient client, ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String claimId = (String) vars.get("claimId");
        log.info("[BPMN] assign-workshop for claim={}", claimId);

        // In production: call Partner Service to list certified workshops
        String workshopId = "WS-001";
        String workshopName = "Delhi Auto Repairs Pvt. Ltd.";

        client.newCompleteCommand(job.getKey())
            .variables(Map.of(
                "workshopId",   workshopId,
                "workshopName", workshopName,
                "workshopAddress", "Plot 12, Industrial Area, Delhi NCR"
            ))
            .send()
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("assign-workshop failed: {}", ex.getMessage());
                else log.info("Workshop {} assigned to claim={}", workshopId, claimId);
            });
    }

    /** BPMN task: process-payment — trigger Stripe payment via Payment Service. */
    @JobWorker(type = "process-payment")
    public void processPayment(JobClient client, ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String claimId = (String) vars.get("claimId");
        log.info("[BPMN] process-payment for claim={}", claimId);

        // In production: call Payment Service to initiate Stripe settlement
        String paymentRef = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        client.newCompleteCommand(job.getKey())
            .variables(Map.of(
                "paymentRef",       paymentRef,
                "paymentProcessed", true,
                "paymentDate",      LocalDateTime.now().toString()
            ))
            .send()
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("process-payment failed: {}", ex.getMessage());
                else log.info("Payment {} processed for claim={}", paymentRef, claimId);
            });
    }

    /** BPMN task: close-claim — publishes claim.closed event. */
    @JobWorker(type = "close-claim")
    public void closeClaim(JobClient client, ActivatedJob job) {
        Map<String, Object> vars = job.getVariablesAsMap();
        String claimId = (String) vars.get("claimId");
        log.info("[BPMN] close-claim for claim={}", claimId);

        // Publish claim.closed to Kafka
        ClaimClosedEvent event = ClaimClosedEvent.builder()
            .eventId(UUID.randomUUID())
            .claimId(UUID.fromString(claimId))
            .claimNumber((String) vars.getOrDefault("claimNumber", ""))
            .closedBy("WORKFLOW_ENGINE")
            .closureReason("PAYMENT_SETTLED")
            .closedAt(LocalDateTime.now())
            .build();

        kafkaTemplate.send("claim.closed", claimId, event);

        client.newCompleteCommand(job.getKey())
            .variables(Map.of("claimClosed", true))
            .send()
            .whenComplete((r, ex) -> {
                if (ex != null) log.error("close-claim failed: {}", ex.getMessage());
                else log.info("Claim {} closed successfully", claimId);
            });
    }

    /** BPMN task: escalate-surveyor — notifies case manager of overdue assignment. */
    @JobWorker(type = "escalate-surveyor")
    public void escalateSurveyor(JobClient client, ActivatedJob job) {
        String claimId = (String) job.getVariablesAsMap().get("claimId");
        log.warn("[BPMN] ESCALATION: Surveyor overdue for claim={}", claimId);

        // In production: send alert to Case Manager via Notification Service
        client.newCompleteCommand(job.getKey())
            .variables(Map.of("surveyorEscalated", true))
            .send()
            .exceptionally(ex -> { log.error("escalate-surveyor error: {}", ex.getMessage()); return null; });
    }

    /** BPMN task: escalate-adjustor — notifies case manager of overdue decision. */
    @JobWorker(type = "escalate-adjustor")
    public void escalateAdjustor(JobClient client, ActivatedJob job) {
        String claimId = (String) job.getVariablesAsMap().get("claimId");
        log.warn("[BPMN] ESCALATION: Adjustor decision overdue for claim={}", claimId);

        // In production: send alert to Case Manager via Notification Service
        client.newCompleteCommand(job.getKey())
            .variables(Map.of("adjustorEscalated", true))
            .send()
            .exceptionally(ex -> { log.error("escalate-adjustor error: {}", ex.getMessage()); return null; });
    }
}
