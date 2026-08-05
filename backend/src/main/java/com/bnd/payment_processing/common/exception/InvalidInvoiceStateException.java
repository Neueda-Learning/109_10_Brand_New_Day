package com.bnd.payment_processing.common.exception;

/**
 * Thrown when an invoice is not in a state that allows the requested operation
 * (e.g. creating a payment for an invoice that is already PAID). Mapped to 409
 * INVALID_INVOICE_STATE (spec.md Section 10.7).
 */
public class InvalidInvoiceStateException extends RuntimeException {

    public InvalidInvoiceStateException(String message) {
        super(message);
    }
}
