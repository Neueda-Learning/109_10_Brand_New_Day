package com.bnd.payment_processing.invoice.model;

/**
 * Invoice lifecycle states (spec.md Section 9.1 / product.md Section 9.1).
 * DRAFT is not currently produced by the API (invoices are created ISSUED
 * immediately) but is kept for schema/spec parity and potential future use.
 */
public enum InvoiceStatus {
    DRAFT,
    ISSUED,
    PAYMENT_PENDING,
    PAID,
    FAILED,
    REFUND_REQUESTED,
    REFUNDED
}
