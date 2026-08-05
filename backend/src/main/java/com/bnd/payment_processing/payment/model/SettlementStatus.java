package com.bnd.payment_processing.payment.model;

/**
 * Settlement lifecycle for {@code payments.settlement_status} (spec.md Section 9.3
 * / product.md Section 9.3). Independent from {@link PaymentStatus} - a payment
 * only starts moving through settlement once its own status reaches COMPLETED.
 */
public enum SettlementStatus {
    NOT_READY,
    PENDING,
    SETTLED,
    FAILED
}
