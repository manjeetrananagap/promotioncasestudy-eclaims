package com.nagarro.eclaims.claims;

import com.nagarro.eclaims.claims.dto.ClaimRequest;
import com.nagarro.eclaims.claims.dto.ClaimResponse;
import com.nagarro.eclaims.claims.entity.ClaimStatus;
import com.nagarro.eclaims.claims.repository.ClaimRepository;
import com.nagarro.eclaims.claims.service.ClaimService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ClaimService.
 * Uses Testcontainers (real PostgreSQL) + EmbeddedKafka (in-memory broker).
 * No mocks — tests hit the real DB and real Kafka.
 */
@SpringBootTest
@Testcontainers
@EmbeddedKafka(partitions = 1,
    topics = {"claim.submitted", "claim.validated", "claim.approved", "claim.closed", "audit.events"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClaimsServiceIntegrationTest {

    // Testcontainers spins up a real PostgreSQL 15 instance for tests
    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("claims_db")
            .withUsername("eclaims")
            .withPassword("eclaims_dev_2024");

    // Inject the container's dynamic port into Spring datasource config
    @DynamicPropertySource
    static void postgresProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",      postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Disable Keycloak JWT check in tests
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "");
        // Use embedded Kafka broker injected by @EmbeddedKafka
        registry.add("spring.kafka.bootstrap-servers",
            () -> System.getProperty("spring.embedded.kafka.brokers", "localhost:9092"));
    }

    @Autowired ClaimService     claimService;
    @Autowired ClaimRepository  claimRepository;

    @BeforeEach
    void clean() { claimRepository.deleteAll(); }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test @Order(1)
    @DisplayName("submitClaim: persists claim with VALIDATED status (auto-validation in POC)")
    void submitClaim_persistsAndAutoValidates() {
        ClaimResponse res = claimService.submitClaim(
            buildRequest(), "user-001", "Rajesh Verma", "rajesh@test.com", "+91-9876543210");

        assertThat(res.getId()).isNotNull();
        assertThat(res.getClaimNumber()).matches("CLM-\\d{4}-\\d{6}");
        // POC auto-validates immediately after submit
        assertThat(res.getStatus()).isEqualTo(ClaimStatus.VALIDATED);
        assertThat(claimRepository.count()).isEqualTo(1);
    }

    @Test @Order(2)
    @DisplayName("submitClaim: generates unique sequential claim numbers")
    void submitClaim_uniqueClaimNumbers() {
        ClaimResponse r1 = claimService.submitClaim(buildRequest(), "u1", "User1", "u1@t.com", null);
        ClaimResponse r2 = claimService.submitClaim(buildRequest(), "u2", "User2", "u2@t.com", null);

        assertThat(r1.getClaimNumber()).isNotEqualTo(r2.getClaimNumber());
    }

    @Test @Order(3)
    @DisplayName("getById: returns claim with full status history")
    void getById_returnsWithHistory() {
        ClaimResponse submitted = claimService.submitClaim(
            buildRequest(), "user-001", "Rajesh", "r@t.com", null);

        ClaimResponse detail = claimService.getById(submitted.getId());

        assertThat(detail.getStatusHistory()).isNotEmpty();
        // Should have SUBMITTED → VALIDATED entries
        assertThat(detail.getStatusHistory()).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test @Order(4)
    @DisplayName("approve: transitions ASSESSED claim to APPROVED with financial fields")
    void approve_setsFinancials() {
        ClaimResponse sub = claimService.submitClaim(
            buildRequest(), "u1", "Rajesh", "r@t.com", null);

        // Force to ASSESSED for test
        var entity = claimRepository.findById(sub.getId()).orElseThrow();
        entity.setStatus(ClaimStatus.ASSESSED);
        claimRepository.save(entity);

        ClaimResponse approved = claimService.approve(
            sub.getId(), new BigDecimal("50000"), new BigDecimal("5000"),
            "Meena Kapoor", "meena@test.com", "+91-9876543211");

        assertThat(approved.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(approved.getApprovedAmount()).isEqualByComparingTo("50000");
        assertThat(approved.getInsurerContribution()).isEqualByComparingTo("45000");
        assertThat(approved.getCustomerContribution()).isEqualByComparingTo("5000");
    }

    @Test @Order(5)
    @DisplayName("getByUser: returns only that user's claims paginated")
    void getByUser_filtersCorrectly() {
        claimService.submitClaim(buildRequest(), "u1", "U1", "u1@t.com", null);
        claimService.submitClaim(buildRequest(), "u1", "U1", "u1@t.com", null);
        claimService.submitClaim(buildRequest(), "u2", "U2", "u2@t.com", null);

        var page = claimService.getByUser("u1",
            org.springframework.data.domain.PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
    }

    @Test @Order(6)
    @DisplayName("getById: throws ClaimNotFoundException for unknown id")
    void getById_notFound() {
        assertThatThrownBy(() -> claimService.getById(java.util.UUID.randomUUID()))
            .isInstanceOf(com.nagarro.eclaims.claims.exception.ClaimNotFoundException.class);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private ClaimRequest buildRequest() {
        return ClaimRequest.builder()
            .policyId("POL-TEST-001")
            .vehicleReg("DL01AB1234")
            .vehicleMake("Toyota").vehicleModel("Innova")
            .accidentLat(new BigDecimal("28.6139"))
            .accidentLng(new BigDecimal("77.2090"))
            .accidentAddress("Connaught Place, New Delhi")
            .incidentDate(LocalDate.now().minusDays(1))
            .incidentDescription("Rear-end collision at traffic signal near CP.")
            .build();
    }
}
