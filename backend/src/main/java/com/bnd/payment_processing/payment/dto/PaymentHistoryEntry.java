package com.bnd.payment_processing.payment.dto;

import com.bnd.payment_processing.payment.model.PaymentStatus;

import java.time.Instant;

/**
 * A single entry in a payment's status-history timeline
 * (spec.md Section 10.4 - {@code GET /api/payments/{id}/history}).
 */
public class PaymentHistoryEntry {

    private PaymentStatus fromStatus;
    private PaymentStatus toStatus;
    private Instant changedAt;
    private String triggeredBy;
    private String note;

    public PaymentHistoryEntry() {
    }

    public PaymentStatus getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(PaymentStatus fromStatus) {
        this.fromStatus = fromStatus;
    }

    public PaymentStatus getToStatus() {
        return toStatus;
    }

    public void setToStatus(PaymentStatus toStatus) {
        this.toStatus = toStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(String triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
