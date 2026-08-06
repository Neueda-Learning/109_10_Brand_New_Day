package com.bnd.payment_processing.payment.model;

/**
 * Status of a registered bank account (added 2026-08-06, bank-grade validation
 * hardening). Simulates a core-banking account status check without real auth.
 */
public enum AccountStatus {
    ACTIVE,
    BLOCKED,
    CLOSED
}

