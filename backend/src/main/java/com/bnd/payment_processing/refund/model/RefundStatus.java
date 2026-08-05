package com.bnd.payment_processing.refund.model;

/**
 * Processing-side lifecycle for a refund (product.md Section 9.4), stored in
 * {@code refunds.status}. Independent from {@code refunds.approval_status}
 * (business approve/reject decision, {@link com.bnd.payment_processing.payment.model.ApprovalStatus}).
 */
public enum RefundStatus {
    REQUESTED,
    PROCESSING,
    COMPLETED,
    FAILED,
    REJECTED
}
