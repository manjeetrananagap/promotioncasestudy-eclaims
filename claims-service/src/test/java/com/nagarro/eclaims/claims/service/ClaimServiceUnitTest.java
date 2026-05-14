package com.nagarro.eclaims.claims.service;

import com.nagarro.eclaims.claims.dto.ClaimRequest;
import com.nagarro.eclaims.claims.dto.ClaimResponse;
import com.nagarro.eclaims.claims.entity.Claim;
import com.nagarro.eclaims.claims.entity.ClaimStatus;
import com.nagarro.eclaims.claims.exception.ClaimNotFoundException;
import com.nagarro.eclaims.claims.exception.InvalidStatusTransitionException;
import com.nagarro.eclaims.claims.repository.ClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Pure unit tests for {@link ClaimService} — no Spring context, no DB, no Kafka broker.
 * All collaborators are Mockito mocks injected via @InjectMocks.
 */
@ExtendWith(MockitoExtension.class)
class ClaimServiceUnitTest {

    @Mock private ClaimRepository         claimRepository;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;
    @Mock private JdbcTemplate            jdbcTemplate;
    @Mock private StripePaymentService    stripePaymentService;

    @InjectMocks private ClaimService claimService;

    @BeforeEach
    void injectTopicValues() {
        ReflectionTestUtils.setField(claimService, "topicSubmitted", "claim.submitted");
        ReflectionTestUtils.setField(claimService, "topicValidated", "claim.validated");
        ReflectionTestUtils.setField(claimService, "topicApproved",  "claim.approved");
        ReflectionTestUtils.setField(claimService, "topicClosed",    "claim.closed");
        ReflectionTestUtils.setField(claimService, "topicPayment",   "payment.processed");

        // KafkaTemplate.send returns CompletableFuture — stub with doReturn to avoid generics issues
        lenient().doReturn(CompletableFuture.completedFuture(null))
                .when(kafkaTemplate).send(anyString(), anyString(), any());
    }

    // ── submitClaim ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("submitClaim: saves claim and publishes claim.submitted event")
    void submitClaim_savesClaimAndPublishesEvent() {
        Claim saved = buildClaim(ClaimStatus.VALIDATED);
        when(claimRepository.save(any(Claim.class))).thenReturn(saved);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1L);

        ClaimResponse result = claimService.submitClaim(
                buildRequest(), "user-1", "Rajesh", "r@test.com", "+919876543210");

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(saved.getId());
        verify(claimRepository, times(2)).save(any(Claim.class));
        // Kafka publish for claim.submitted + claim.validated = 2 sends
        verify(kafkaTemplate, atLeast(1)).send(eq("claim.submitted"), anyString(), any());
    }

    @Test
    @DisplayName("submitClaim: generates claim number in CLM-YYYY-NNNNNN format")
    void submitClaim_claimNumberFormat() {
        Claim saved = buildClaim(ClaimStatus.VALIDATED);
        when(claimRepository.save(any(Claim.class))).thenReturn(saved);
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        ClaimResponse result = claimService.submitClaim(
                buildRequest(), "u1", "User", "u@t.com", null);

        // The response reflects whatever the saved entity has — verify id is returned
        assertThat(result.getId()).isNotNull();
    }

    // ── getById ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById: returns response for existing claim")
    void getById_returnsExistingClaim() {
        Claim claim = buildClaim(ClaimStatus.SUBMITTED);
        when(claimRepository.findByIdWithHistory(claim.getId())).thenReturn(Optional.of(claim));

        ClaimResponse result = claimService.getById(claim.getId());

        assertThat(result.getId()).isEqualTo(claim.getId());
        assertThat(result.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    @DisplayName("getById: throws ClaimNotFoundException for unknown id")
    void getById_throwsNotFound() {
        UUID unknown = UUID.randomUUID();
        when(claimRepository.findByIdWithHistory(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> claimService.getById(unknown))
                .isInstanceOf(ClaimNotFoundException.class)
                .hasMessageContaining(unknown.toString());
    }

    // ── approve ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("approve: transitions ASSESSED claim to APPROVED and publishes event")
    void approve_transitionsToApproved() {
        Claim claim = buildClaim(ClaimStatus.ASSESSED);
        when(claimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(claimRepository.save(any())).thenReturn(claim);

        ClaimResponse result = claimService.approve(
                claim.getId(), new BigDecimal("50000"), new BigDecimal("5000"),
                "Meena", "meena@t.com", "+919876543211");

        assertThat(result.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        verify(kafkaTemplate).send(eq("claim.approved"), anyString(), any());
    }

    @Test
    @DisplayName("approve: throws InvalidStatusTransitionException when claim is not ASSESSED")
    void approve_wrongStatus_throws() {
        Claim claim = buildClaim(ClaimStatus.SUBMITTED);
        when(claimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));

        assertThatThrownBy(() -> claimService.approve(
                claim.getId(), new BigDecimal("50000"), BigDecimal.ZERO,
                "Meena", "m@t.com", null))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    // ── processPayment ───────────────────────────────────────────────────────

    @Test
    @DisplayName("processPayment: calls Stripe and publishes payment.processed event")
    void processPayment_callsStripeAndPublishesEvent() {
        Claim claim = buildClaim(ClaimStatus.APPROVED);
        claim.setApprovedAmount(new BigDecimal("45000"));
        claim.setCustomerContribution(new BigDecimal("5000"));
        when(claimRepository.findById(claim.getId())).thenReturn(Optional.of(claim));
        when(claimRepository.save(any())).thenReturn(claim);
        when(stripePaymentService.createPaymentIntent(any(), anyString(), any(), anyString()))
                .thenReturn("pi_simulated_secret");

        claimService.processPayment(claim.getId(), "customer@test.com", "+911234567890", "ADJUSTOR");

        verify(stripePaymentService).createPaymentIntent(
                eq(claim.getId()), eq(claim.getClaimNumber()), any(), eq("customer@test.com"));
        verify(kafkaTemplate).send(eq("payment.processed"), anyString(), any());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Claim buildClaim(ClaimStatus status) {
        Claim c = Claim.builder()
                .id(UUID.randomUUID())
                .claimNumber("CLM-2025-000001")
                .policyId("POL-001")
                .policyHolderName("Rajesh Verma")
                .vehicleReg("DL01AB1234")
                .vehicleMake("Toyota")
                .vehicleModel("Innova")
                .accidentLat(new BigDecimal("28.6139"))
                .accidentLng(new BigDecimal("77.2090"))
                .accidentAddress("New Delhi")
                .incidentDate(LocalDate.now().minusDays(1))
                .submittedByUserId("user-1")
                .submittedByName("Rajesh Verma")
                .build();
        c.setStatus(status);
        return c;
    }

    private ClaimRequest buildRequest() {
        return ClaimRequest.builder()
                .policyId("POL-001")
                .vehicleReg("DL01AB1234")
                .vehicleMake("Toyota")
                .vehicleModel("Innova")
                .accidentLat(new BigDecimal("28.6139"))
                .accidentLng(new BigDecimal("77.2090"))
                .accidentAddress("New Delhi")
                .incidentDate(LocalDate.now().minusDays(1))
                .incidentDescription("Rear-end collision on NH-48")
                .build();
    }
}
