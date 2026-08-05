package com.bnd.payment_processing.payment.model;

/**
 * Supported payment method types (spec.md Section 7.3 / product.md Section 7.3
 * and Section 5's hard constraint "Payment methods are Card and Bank Transfer
 * only"). Used by {@code payment_methods.method_type}. UPI/wallets/autopay are
 * explicitly future scope (product.md Section 20).
 */
public enum PaymentMethodType {
    CARD,
    BANK_TRANSFER
}
