package com.bnd.payment_processing.payment.model;

/**
 * Distinguishes an ordinary payment from a refund (spec.md Section 7 / 8.1).
 * A REFUND row always has originalPaymentId set and can never itself be refunded.
 */
public enum PaymentType {
    PAYMENT,
    REFUND
}
