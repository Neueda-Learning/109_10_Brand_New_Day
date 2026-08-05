package com.bnd.payment_processing.payment.model;

/**
 * Payment method tag (spec.md Section 7, added 2026-08-05). A single supported
 * value today - the enum exists so future methods (e.g. CARD/UPI/WALLET) can be
 * added later without a schema or API shape change.
 */
public enum PaymentMethod {
    BANK_TRANSFER
}
