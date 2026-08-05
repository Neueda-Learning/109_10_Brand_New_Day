package com.bnd.payment_processing.paymentmethod.model;

import com.bnd.payment_processing.payment.model.PaymentMethodType;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model mapped to the {@code payment_methods} table (spec.md Section 7.3 /
 * product.md Section 7.3). Stores only safe, tokenized, demo references - never a
 * full card or bank account number (spec.md Section 15 / product.md Section 15).
 */
public class PaymentMethod {

    private UUID id;
    private UUID customerId;
    private PaymentMethodType methodType;
    private String displayLabel;
    private String maskedIdentifier;
    private String tokenRef;
    private String provider;
    private Instant createdAt;
    private Instant updatedAt;

    public PaymentMethod() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public PaymentMethodType getMethodType() {
        return methodType;
    }

    public void setMethodType(PaymentMethodType methodType) {
        this.methodType = methodType;
    }

    public String getDisplayLabel() {
        return displayLabel;
    }

    public void setDisplayLabel(String displayLabel) {
        this.displayLabel = displayLabel;
    }

    public String getMaskedIdentifier() {
        return maskedIdentifier;
    }

    public void setMaskedIdentifier(String maskedIdentifier) {
        this.maskedIdentifier = maskedIdentifier;
    }

    public String getTokenRef() {
        return tokenRef;
    }

    public void setTokenRef(String tokenRef) {
        this.tokenRef = tokenRef;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
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
