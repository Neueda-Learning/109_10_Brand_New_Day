package com.bnd.payment_processing.common.exception;

import java.util.UUID;

/**
 * Thrown when a payment id does not exist. Mapped to 404 PAYMENT_NOT_FOUND
 * (spec.md Section 10.7).
 */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("Payment with id " + id + " was not found");
    }

    public PaymentNotFoundException(String message) {
        super(message);
    }
}
