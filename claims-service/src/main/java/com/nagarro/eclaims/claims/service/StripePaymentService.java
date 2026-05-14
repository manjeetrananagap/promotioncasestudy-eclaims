package com.nagarro.eclaims.claims.service;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Processes electronic payments via Stripe (PCI-DSS Level 1).
 *
 * Card details never reach eClaims servers — Stripe.js tokenises them client-side.
 * This service creates PaymentIntents using Stripe's server-side SDK.
 *
 * In local dev (STRIPE_ENABLED=false) payments are simulated with a log message.
 * In production, STRIPE_SECRET_KEY is injected from GCP Secret Manager via
 * environment variable STRIPE_SECRET_KEY.
 */
@Service
@Slf4j
public class StripePaymentService {

    @Value("${eclaims.stripe.secret-key:}")
    private String secretKey;

    @Value("${eclaims.stripe.enabled:false}")
    private boolean stripeEnabled;

    @PostConstruct
    public void init() {
        if (stripeEnabled && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
            log.info("Stripe SDK initialised");
        } else {
            log.info("Stripe disabled or API key not configured — payment simulation mode");
        }
    }

    /**
     * Initiates a Stripe PaymentIntent for the insurer-contribution portion of a claim.
     *
     * @param claimId            UUID of the claim (used as idempotency key)
     * @param claimNumber        human-readable claim reference
     * @param amountInRupees     payment amount in INR (will be converted to paise for Stripe)
     * @param customerEmail      recipient for Stripe payment receipt
     * @return Stripe PaymentIntent client_secret to return to the frontend for confirmation
     */
    public String createPaymentIntent(UUID claimId, String claimNumber,
                                       BigDecimal amountInRupees, String customerEmail) {
        if (!stripeEnabled) {
            // Simulation mode — return a mock client secret for local dev
            String mockSecret = "pi_simulated_" + claimId + "_secret_dev";
            log.info("[DEV] Stripe payment simulated — claim:{} amount:₹{} mockSecret:{}",
                    claimNumber, amountInRupees, mockSecret);
            return mockSecret;
        }

        try {
            // Stripe amounts are in smallest currency unit — paise for INR
            long amountPaise = amountInRupees.multiply(BigDecimal.valueOf(100)).longValue();

            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountPaise)
                    .setCurrency("inr")
                    .setReceiptEmail(customerEmail)
                    .setDescription("eClaims settlement for claim " + claimNumber)
                    // Idempotency: same claimId always creates the same PaymentIntent
                    .putMetadata("claimId", claimId.toString())
                    .putMetadata("claimNumber", claimNumber)
                    .addPaymentMethodType("card")
                    .build();

            PaymentIntent intent = PaymentIntent.create(params,
                    com.stripe.net.RequestOptions.builder()
                            .setIdempotencyKey("pi-" + claimId)
                            .build());

            log.info("Stripe PaymentIntent created — claimId:{} intentId:{} amount:₹{}",
                    claimId, intent.getId(), amountInRupees);

            return intent.getClientSecret();

        } catch (StripeException ex) {
            log.error("Stripe payment failed — claimId:{} error:{}", claimId, ex.getMessage());
            throw new RuntimeException("Payment processing failed: " + ex.getMessage(), ex);
        }
    }
}
