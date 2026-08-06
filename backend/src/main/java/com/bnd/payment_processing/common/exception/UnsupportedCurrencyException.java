package com.bnd.payment_processing.common.exception;

/** Thrown when a payment's currency has no seeded exchange_rates row to settle it in INR. */
public class UnsupportedCurrencyException extends RuntimeException {
    public UnsupportedCurrencyException(String currency) {
        super("Currency " + currency + " is not supported (no exchange rate configured)");
    }
}

