package com.bnd.payment_processing.common.exception;

/**
 * Thrown when a card fails validation (blocked, expired, or CVV mismatch). Deliberately
 * generic like a real processor's "declined" response - never reveals which exact
 * check failed in the top-level exception type, only in the message (demo/debug only).
 */
public class CardDeclinedException extends RuntimeException {
    public CardDeclinedException(String message) {
        super(message);
    }
}

