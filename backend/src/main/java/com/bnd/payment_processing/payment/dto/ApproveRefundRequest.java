package com.bnd.payment_processing.payment.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/payments/{id}/refund/approve} (spec.md
 * Section 10.8, added 2026-08-05).
 */
public class ApproveRefundRequest {

    @NotBlank
    private String approvedBy;

    private String note;

    public ApproveRefundRequest() {
    }

    public String getApprovedBy() {
        return approvedBy;
    }

    public void setApprovedBy(String approvedBy) {
        this.approvedBy = approvedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
