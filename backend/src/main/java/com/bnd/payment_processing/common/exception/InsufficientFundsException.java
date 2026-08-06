package com.bnd.payment_processing.common.exception;

import java.math.BigDecimal;

/**
 * Thrown when an account does not have enough balance to fund a payment (added
 * 2026-08-06 hotfix - bank-grade solvency guard, previously missing entirely).
 * Raised both as a fail-fast check at creation time (best-effort, point-in-time)
 * and as the authoritative atomic check at settlement time in
 * PaymentServiceImpl.processTransition() via AccountRepository.debitIfSufficient().
 */
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String accountNumber, BigDecimal required, BigDecimal available) {
        super("Account " + accountNumber + " has insufficient funds: requires " + required
                + " but available balance is " + available);
    }
}

