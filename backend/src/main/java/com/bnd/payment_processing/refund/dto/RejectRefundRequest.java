package com.bnd.payment_processing.refund.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/refunds/{id}/reject} (product.md Section 10.2).
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
