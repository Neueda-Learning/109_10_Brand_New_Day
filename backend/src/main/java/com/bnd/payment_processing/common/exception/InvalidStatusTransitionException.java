package com.bnd.payment_processing.common.exception;

/**
 * Thrown when a status transition is not allowed by the state machine in
 * spec.md Section 8 (e.g. transitioning from a terminal state, or invalid use
 * of targetStatus/errorCode per Section 8.2). Mapped to 409 INVALID_STATUS_TRANSITION
 * (spec.md Section 10.7).
 */
public class InvalidStatusTransitionException extends RuntimeException {

    public InvalidStatusTransitionException(String message) {
        super(message);
    }
}
