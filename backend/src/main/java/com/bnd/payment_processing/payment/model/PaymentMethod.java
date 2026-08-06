package com.bnd.payment_processing.payment.model;

/**
 * Payment method tag (spec.md Section 7, added 2026-08-05).
 * CARD added 2026-08-06 (bank-grade hardening) - CVV is never persisted anywhere;
 * it is validated transiently at creation time only (see PaymentServiceImpl).
 */
public enum PaymentMethod {
    BANK_TRANSFER,
    CARD
}
