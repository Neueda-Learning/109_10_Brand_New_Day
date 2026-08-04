package com.bnd.payment_processing.common.exception;

import com.bnd.payment_processing.payment.model.Payment;

/**
 * Signals that a {@code POST /api/payments} call reused an existing
 * idempotency_key. This is a short-circuit, not an error: the caller returns
 * the original payment resource with HTTP 200 (spec.md Section 10.7) rather
 * than letting this bubble up as an ErrorResponse.
 */
public class DuplicatePaymentException extends RuntimeException {

    private final Payment existingPayment;

    public DuplicatePaymentException(Payment existingPayment) {
        super("Payment with idempotency key " + existingPayment.getIdempotencyKey() + " already exists");
        this.existingPayment = existingPayment;
    }

    public Payment getExistingPayment() {
        return existingPayment;
    }
}
