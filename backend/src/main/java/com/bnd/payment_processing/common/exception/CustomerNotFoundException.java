package com.bnd.payment_processing.common.exception;

import java.util.UUID;

/**
 * Thrown when a customer id does not resolve to a seeded customer row. Mapped to
 * 404 CUSTOMER_NOT_FOUND (spec.md Section 10.7).
 */
public class CustomerNotFoundException extends RuntimeException {

    public CustomerNotFoundException(UUID id) {
        super("Customer with id " + id + " was not found");
    }

    public CustomerNotFoundException(String message) {
        super(message);
    }
}
