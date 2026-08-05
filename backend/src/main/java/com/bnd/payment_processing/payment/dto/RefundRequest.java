package com.bnd.payment_processing.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/payments/{id}/refund} (spec.md Section 10.6).
 */
public class RefundRequest {

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal amount;

    private String reason;

    // Added 2026-08-05 (spec.md Section 10.6, v2.2): optional. If provided and it
    // already exists on a prior refund attempt, the create endpoint short-circuits
    // to 200 OK with the existing refund resource instead of creating a duplicate row.
    private String idempotencyKey;

    public RefundRequest() {
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}
