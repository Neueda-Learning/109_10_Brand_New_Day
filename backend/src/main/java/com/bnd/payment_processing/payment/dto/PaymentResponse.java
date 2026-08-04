package com.bnd.payment_processing.payment.dto;

import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body shape shared by every endpoint that returns a payment
 * (spec.md Section 10.1 / 10.2).
 */
public class PaymentResponse {

    private UUID id;
    private String idempotencyKey;
    private String sourceAccount;
    private String destinationAccount;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String errorCode;
    private PaymentType type;
    private UUID originalPaymentId;
    private Instant createdAt;
    private Instant updatedAt;

    public PaymentResponse() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSourceAccount() {
        return sourceAccount;
    }

    public void setSourceAccount(String sourceAccount) {
        this.sourceAccount = sourceAccount;
    }

    public String getDestinationAccount() {
        return destinationAccount;
    }

    public void setDestinationAccount(String destinationAccount) {
        this.destinationAccount = destinationAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public PaymentType getType() {
        return type;
    }

    public void setType(PaymentType type) {
        this.type = type;
    }

    public UUID getOriginalPaymentId() {
        return originalPaymentId;
    }

    public void setOriginalPaymentId(UUID originalPaymentId) {
        this.originalPaymentId = originalPaymentId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
