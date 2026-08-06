package com.bnd.payment_processing.common.exception;

/** Thrown when an account_number referenced by a payment doesn't exist in the registry. */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String accountNumber) {
        super("Account " + accountNumber + " was not found");
    }
}

