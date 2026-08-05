package com.bnd.payment_processing.refund.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/refunds/{id}/approve} (product.md Section 10.2).
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
