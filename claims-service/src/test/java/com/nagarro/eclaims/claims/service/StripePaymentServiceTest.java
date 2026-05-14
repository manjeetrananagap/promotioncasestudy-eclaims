package com.nagarro.eclaims.claims.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StripePaymentService}.
 *
 * Tests cover the simulation path (stripeEnabled=false) which is the only
 * safely unit-testable path without wiring a real Stripe HTTP call.
 * Integration tests against Stripe test-mode keys belong in a separate
 * integration test suite run with STRIPE_TEST_KEY secret injected.
 */
class StripePaymentServiceTest {

    private StripePaymentService service;

    @BeforeEach
    void setUp() {
        service = new StripePaymentService();
        ReflectionTestUtils.setField(service, "stripeEnabled", false);
        ReflectionTestUtils.setField(service, "secretKey", "");
        service.init();   // @PostConstruct
    }

    @Test
    @DisplayName("createPaymentIntent: simulation mode returns mock client secret")
    void createPaymentIntent_simulationMode_returnsMockSecret() {
        UUID claimId = UUID.randomUUID();

        String secret = service.createPaymentIntent(
                claimId, "CLM-2025-000001", new BigDecimal("45000"), "customer@test.com");

        assertThat(secret).isNotBlank();
        assertThat(secret).startsWith("pi_simulated_");
        assertThat(secret).contains(claimId.toString());
    }

    @Test
    @DisplayName("createPaymentIntent: simulation mode never throws")
    void createPaymentIntent_simulationMode_neverThrows() {
        assertThatCode(() ->
                service.createPaymentIntent(
                        UUID.randomUUID(), "CLM-X", BigDecimal.TEN, null)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("createPaymentIntent: same claimId always yields same mock secret prefix")
    void createPaymentIntent_deterministicSimulation() {
        UUID claimId = UUID.randomUUID();
        String s1 = service.createPaymentIntent(claimId, "CLM-1", BigDecimal.TEN, "a@b.com");
        String s2 = service.createPaymentIntent(claimId, "CLM-1", BigDecimal.TEN, "a@b.com");

        // In simulation mode the secret is deterministic per claimId
        assertThat(s1).isEqualTo(s2);
    }
}
