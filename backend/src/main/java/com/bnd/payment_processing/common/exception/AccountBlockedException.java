package com.bnd.payment_processing.common.exception;

/** Thrown when an account exists but is BLOCKED/CLOSED and cannot participate in a payment. */
public class AccountBlockedException extends RuntimeException {
    public AccountBlockedException(String accountNumber, String status) {
        super("Account " + accountNumber + " is " + status + " and cannot send or receive payments");
    }
}

