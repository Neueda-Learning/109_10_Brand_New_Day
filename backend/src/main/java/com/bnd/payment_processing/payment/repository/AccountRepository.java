package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Account;

import java.util.Optional;

/**
 * Persistence contract for the {@code accounts} reference registry (added 2026-08-06).
 * Read-mostly - used to validate source/destination account numbers on payments.
 */
public interface AccountRepository {

    Optional<Account> findByAccountNumber(String accountNumber);
}

