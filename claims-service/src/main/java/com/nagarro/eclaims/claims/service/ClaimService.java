package com.nagarro.eclaims.claims.service;

import com.nagarro.eclaims.claims.dto.ClaimRequest;
import com.nagarro.eclaims.claims.dto.ClaimResponse;
import com.nagarro.eclaims.claims.entity.Claim;
import com.nagarro.eclaims.claims.entity.ClaimStatus;
import com.nagarro.eclaims.claims.exception.ClaimNotFoundException;
import com.nagarro.eclaims.claims.exception.InvalidStatusTransitionException;
import com.nagarro.eclaims.claims.repository.ClaimRepository;
import com.nagarro.eclaims.events.ClaimApprovedEvent;
import com.nagarro.eclaims.events.ClaimSubmittedEvent;
import com.nagarro.eclaims.events.ClaimValidatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Year;
import java.util.UUID;

/**
 * Claims business service.
 *
 * Transaction strategy:
 * - All writes are @Transactional (default REQUIRED)
 * - All reads use @Transactional(readOnly = true) — skips dirty-check overhead
 * - Kafka publish happens after DB write. At-least-once delivery accepted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClaimService {

    private final ClaimRepository   claimRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final JdbcTemplate      jdbcTemplate;

    @Value("${eclaims.kafka.topics.claim-submitted}")  private String topicSubmitted;
    @Value("${eclaims.kafka.topics.claim-validated}")  private String topicValidated;
    @Value("${eclaims.kafka.topics.claim-approved}")   private String topicApproved;
    @Value("${eclaims.kafka.topics.claim-closed}")     private String topicClosed;

    // ── Submit FNOL ──────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse submitClaim(ClaimRequest req, String userId,
                                     String userName, String email, String phone) {

        log.info("FNOL — policy:{} vehicle:{} user:{}", req.getPolicyId(), req.getVehicleReg(), userId);

        Claim claim = Claim.builder()
                .claimNumber(generateClaimNumber())
                .policyId(req.getPolicyId())
                .policyHolderName(userName)
                .vehicleReg(req.getVehicleReg())
                .vehicleMake(req.getVehicleMake())
                .vehicleModel(req.getVehicleModel())
                .accidentLat(req.getAccidentLat())
                .accidentLng(req.getAccidentLng())
                .accidentAddress(req.getAccidentAddress())
                .incidentDate(req.getIncidentDate())
                .incidentDescription(req.getIncidentDescription())
                .submittedByUserId(userId)
                .submittedByName(userName)
                .build();

        claim.transitionTo(ClaimStatus.SUBMITTED, userName, "FNOL submitted", "REST_API");

        Claim saved = claimRepository.save(claim);
        log.info("Claim saved — id:{} number:{}", saved.getId(), saved.getClaimNumber());

        // Publish Kafka event — key=claimId so all events for same claim land on same partition
        ClaimSubmittedEvent event = ClaimSubmittedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .claimId(saved.getId())
                .claimNumber(saved.getClaimNumber())
                .policyId(saved.getPolicyId())
                .policyHolderName(saved.getPolicyHolderName())
                .policyHolderEmail(email)
                .policyHolderPhone(phone)
                .vehicleReg(saved.getVehicleReg())
                .vehicleMake(saved.getVehicleMake())
                .vehicleModel(saved.getVehicleModel())
                .accidentLat(saved.getAccidentLat())
                .accidentLng(saved.getAccidentLng())
                .accidentAddress(saved.getAccidentAddress())
                .incidentDate(saved.getIncidentDate())
                .incidentDescription(saved.getIncidentDescription())
                .submittedByUserId(userId)
                .build();

        kafkaTemplate.send(topicSubmitted, saved.getId().toString(), event)
                .whenComplete((r, ex) -> {
                    if (ex != null) log.error("Failed to publish claim.submitted: {}", ex.getMessage());
                    else log.debug("Published claim.submitted offset:{}", r.getRecordMetadata().offset());
                });

        // Immediately validate (POC — in prod this would be async policy service call)
        validateClaimInternal(saved, email);

        return ClaimResponse.fromEntity(saved);
    }

    // ── Validate ─────────────────────────────────────────────────────────────

    private void validateClaimInternal(Claim claim, String email) {
        claim.transitionTo(ClaimStatus.VALIDATED, "SYSTEM", "Policy auto-validated (POC)", "REST_API");
        claimRepository.save(claim);

        kafkaTemplate.send(topicValidated, claim.getId().toString(),
            ClaimValidatedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .claimId(claim.getId())
                .claimNumber(claim.getClaimNumber())
                .policyId(claim.getPolicyId())
                .policyHolderName(claim.getPolicyHolderName())
                .policyHolderEmail(email)
                .accidentLat(claim.getAccidentLat())
                .accidentLng(claim.getAccidentLng())
                .accidentAddress(claim.getAccidentAddress())
                .build());
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Transactional
    public ClaimResponse approve(UUID claimId, BigDecimal approvedAmount,
                                  BigDecimal deductible, String adjustorName,
                                  String adjustorEmail, String holderPhone) {

        Claim claim = getClaimEntityById(claimId);

        if (claim.getStatus() != ClaimStatus.ASSESSED) {
            throw new InvalidStatusTransitionException(
                    "Claim must be ASSESSED to approve. Current: " + claim.getStatus());
        }

        claim.setApprovedAmount(approvedAmount);
        claim.setDeductibleAmount(deductible);
        claim.setInsurerContribution(approvedAmount.subtract(deductible));
        claim.setCustomerContribution(deductible);
        claim.transitionTo(ClaimStatus.APPROVED, adjustorName,
                "Approved ₹" + approvedAmount, "REST_API");

        Claim saved = claimRepository.save(claim);

        kafkaTemplate.send(topicApproved, claimId.toString(),
            ClaimApprovedEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .occurredAt(LocalDateTime.now())
                .claimId(saved.getId())
                .claimNumber(saved.getClaimNumber())
                .policyHolderName(saved.getPolicyHolderName())
                .policyHolderEmail(adjustorEmail)
                .policyHolderPhone(holderPhone)
                .vehicleReg(saved.getVehicleReg())
                .vehicleMake(saved.getVehicleMake())
                .vehicleModel(saved.getVehicleModel())
                .approvedAmount(approvedAmount)
                .deductibleAmount(deductible)
                .insurerContribution(saved.getInsurerContribution())
                .customerContribution(saved.getCustomerContribution())
                .adjustorName(adjustorName)
                .accidentLat(saved.getAccidentLat())
                .accidentLng(saved.getAccidentLng())
                .accidentAddress(saved.getAccidentAddress())
                .build());

        return ClaimResponse.fromEntity(saved);
    }

    // ── Update status (called by downstream services) ─────────────────────────

    @Transactional
    public ClaimResponse updateStatus(UUID claimId, ClaimStatus newStatus,
                                      String changedBy, String note, String source) {
        Claim claim = getClaimEntityById(claimId);
        claim.transitionTo(newStatus, changedBy, note, source);
        return ClaimResponse.fromEntity(claimRepository.save(claim));
    }

    // ── Reads ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ClaimResponse getById(UUID id) {
        return ClaimResponse.fromEntity(
            claimRepository.findByIdWithHistory(id)
                .orElseThrow(() -> new ClaimNotFoundException("Claim not found: " + id)));
    }

    @Transactional(readOnly = true)
    public ClaimResponse getByNumber(String number) {
        return ClaimResponse.fromEntity(
            claimRepository.findByClaimNumberWithHistory(number)
                .orElseThrow(() -> new ClaimNotFoundException("Claim not found: " + number)));
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> getByUser(String userId, Pageable pageable) {
        return claimRepository
                .findBySubmittedByUserIdAndDeletedFalseOrderByCreatedAtDesc(userId, pageable)
                .map(ClaimResponse::fromEntity);
    }

    @Transactional(readOnly = true)
    public Page<ClaimResponse> getByStatus(ClaimStatus status, Pageable pageable) {
        return claimRepository.findByStatusAndDeletedFalse(status, pageable)
                .map(ClaimResponse::fromEntity);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Claim getClaimEntityById(UUID id) {
        return claimRepository.findById(id)
                .filter(c -> !Boolean.TRUE.equals(c.getDeleted()))
                .orElseThrow(() -> new ClaimNotFoundException("Claim not found: " + id));
    }

    /** CLM-2024-000001 using PostgreSQL sequence */
    private String generateClaimNumber() {
        Long seq = jdbcTemplate.queryForObject("SELECT nextval('claim_number_seq')", Long.class);
        return "CLM-" + Year.now().getValue() + "-" + String.format("%06d", seq);
    }
}
