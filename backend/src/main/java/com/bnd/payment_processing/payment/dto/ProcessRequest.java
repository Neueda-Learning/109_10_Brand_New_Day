package com.bnd.payment_processing.payment.dto;

/**
 * Request body for {@code POST /api/payments/{id}/process} (spec.md Section 10.5).
 * All fields are optional unless the current status is SENT and targetStatus=FAILED,
 * in which case errorCode is required (enforced in the service layer per Section 8.2).
 */
public class ProcessRequest {

    private String targetStatus;
    private String errorCode;
    private String note;

    public ProcessRequest() {
    }

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
