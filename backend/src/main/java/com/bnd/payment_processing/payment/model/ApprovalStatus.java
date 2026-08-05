package com.bnd.payment_processing.payment.model;

/**
 * Refund approval workflow state (spec.md Section 7/8.1 rule 6, added 2026-08-05).
 * Only ever set on {@code type = REFUND} rows; stays {@code null} for
 * {@code type = PAYMENT} rows.
 */
public enum ApprovalStatus {
    PENDING_APPROVAL,
    APPROVED,
    REJECTED
}
