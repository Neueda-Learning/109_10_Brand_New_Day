package com.bnd.payment_processing.common.exception;

/**
 * Thrown when a refund's approver/rejecter is the same actor who requested it
 * (maker-checker / segregation-of-duties simulation - added 2026-08-06).
 */
public class SegregationOfDutiesException extends RuntimeException {
    public SegregationOfDutiesException(String message) {
        super(message);
    }
}

