package com.bnd.payment_processing.common.exception;

import java.util.UUID;

/**
 * Thrown when a refund id does not exist. Mapped to 404 REFUND_NOT_FOUND
 * (spec.md Section 10.7).
 */
public class RefundNotFoundException extends RuntimeException {

    public RefundNotFoundException(UUID id) {
        super("Refund with id " + id + " was not found");
    }

    public RefundNotFoundException(String message) {
        super(message);
    }
}
