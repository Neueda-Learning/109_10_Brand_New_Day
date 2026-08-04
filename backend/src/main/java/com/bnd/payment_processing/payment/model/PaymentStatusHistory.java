package com.bnd.payment_processing.payment.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model mapped to the append-only {@code payment_status_history} table
 * (spec.md Section 7). Rows are never updated or deleted once inserted.
 */
public class PaymentStatusHistory {

    private UUID id;
    private UUID paymentId;
    private PaymentStatus fromStatus;
    private PaymentStatus toStatus;
    private Instant changedAt;
    private String triggeredBy;
    private String note;

    public PaymentStatusHistory() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getPaymentId() {
        return paymentId;
    }

    public void setPaymentId(UUID paymentId) {
        this.paymentId = paymentId;
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
