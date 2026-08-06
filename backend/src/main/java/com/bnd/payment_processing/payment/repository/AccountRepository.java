package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Account;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Persistence contract for the {@code accounts} reference registry (added 2026-08-06).
 * Read-mostly - used to validate source/destination account numbers on payments.
 * Also universal (added 2026-08-06 balance feature): usable by any customer/account,
 * not hardcoded to a specific demo identity.
 */
public interface AccountRepository {

    Optional<Account> findByAccountNumber(String accountNumber);

    /** All accounts belonging to a given customer identity, for balance display. */
    List<Account> findByCustomerRef(String customerRef);

    /**
     * Atomically adjusts an account's balance by {@code delta} (positive = credit,
     * negative = debit) via a single {@code UPDATE ... SET balance = balance + :delta}
     * - MySQL row-level locks this for the duration of the statement, so concurrent
     * adjustments to the same account serialize safely without an explicit
     * {@code SELECT ... FOR UPDATE}. Returns rows affected (0 if account not found).
     */
    int adjustBalance(String accountNumber, BigDecimal delta);

    /**
     * Bank-grade insufficient-funds guard (added 2026-08-06 hotfix): atomically
     * debits {@code amount} from the account's balance ONLY if the current balance
     * is sufficient, via a single {@code UPDATE ... SET balance = balance - :amount
     * WHERE account_number = :accountNumber AND balance >= :amount}. This closes the
     * race window a plain read-then-write check would leave open (two concurrent
     * payments both reading a "sufficient" balance before either commits) and is the
     * single source of truth for "can this payment settle" - not just the account
     * existence/active-status check. Returns 1 if the debit was applied, 0 if the
     * account doesn't exist OR the balance was insufficient.
     */
    int debitIfSufficient(String accountNumber, BigDecimal amount);
}

