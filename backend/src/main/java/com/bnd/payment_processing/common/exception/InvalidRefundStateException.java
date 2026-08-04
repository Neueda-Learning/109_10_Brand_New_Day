package com.bnd.payment_processing.common.exception;

/**
 * Thrown when a refund request violates the rules in spec.md Section 8.1:
 * original payment not COMPLETED, original payment is itself a REFUND,
 * amount &lt;= 0, or cumulative refunded total would exceed the original amount.
 * Mapped to 409 INVALID_REFUND_STATE (spec.md Section 10.7).
 */
public class InvalidRefundStateException extends RuntimeException {

    public InvalidRefundStateException(String message) {
        super(message);
    }
}
