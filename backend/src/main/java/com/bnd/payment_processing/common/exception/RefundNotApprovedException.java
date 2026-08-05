package com.bnd.payment_processing.common.exception;

/**
 * Thrown when {@code POST /api/payments/{id}/process} is attempted on a REFUND
 * payment whose {@code approvalStatus} is not yet {@code APPROVED} (spec.md
 * Section 8.1 rule 6, added 2026-08-05), or when an approve/reject action targets
 * a refund that is no longer {@code PENDING_APPROVAL}.
 * Mapped to 409 REFUND_NOT_APPROVED (spec.md Section 10.7).
 */
public class RefundNotApprovedException extends RuntimeException {

    public RefundNotApprovedException(String message) {
        super(message);
    }
}
