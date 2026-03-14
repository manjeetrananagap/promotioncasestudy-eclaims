package com.nagarro.eclaims.claims.entity;

/** Claim lifecycle state machine. All valid transitions enforced in ClaimService. */
public enum ClaimStatus {
    SUBMITTED, VALIDATED, SURVEYOR_ASSIGNED, ASSESSED,
    APPROVED, REJECTED, WORKSHOP_ASSIGNED, REPAIR_IN_PROGRESS,
    REPAIR_COMPLETED, PAYMENT_PENDING, CLOSED, CANCELLED
}
