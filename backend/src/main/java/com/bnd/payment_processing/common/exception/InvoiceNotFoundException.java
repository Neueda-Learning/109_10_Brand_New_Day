package com.bnd.payment_processing.common.exception;

import java.util.UUID;

/**
 * Thrown when an invoice id does not exist. Mapped to 404 INVOICE_NOT_FOUND
 * (spec.md Section 10.7).
 */
public class InvoiceNotFoundException extends RuntimeException {

    public InvoiceNotFoundException(UUID id) {
        super("Invoice with id " + id + " was not found");
    }

    public InvoiceNotFoundException(String message) {
        super(message);
    }
}
