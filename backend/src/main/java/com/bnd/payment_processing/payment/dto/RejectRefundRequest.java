package com.bnd.payment_processing.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/payments/{id}/refund/reject} (spec.md
 * Section 10.9, added 2026-08-05).
 */
public class RejectRefundRequest {

    @NotBlank
    private String rejectedBy;

    @NotBlank
    private String reason;

    public RejectRefundRequest() {
    }

    public String getRejectedBy() {
        return rejectedBy;
    }

    public void setRejectedBy(String rejectedBy) {
        this.rejectedBy = rejectedBy;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
