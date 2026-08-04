package com.bnd.payment_processing.payment.model;

/**
 * Allowed payment lifecycle states (spec.md Section 8).
 * COMPLETED and FAILED are terminal - no further transitions are allowed.
 */
public enum PaymentStatus {
    CREATED,
    VALIDATED,
    SENT,
    COMPLETED,
    FAILED
}
