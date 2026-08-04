package com.bnd.payment_processing.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/**
 * Request body for {@code POST /api/payments} (spec.md Section 10.1).
 * Validation rules are defined in Section 9 (M1).
 */
public class CreatePaymentRequest {

    @NotBlank
    private String sourceAccount;

    @NotBlank
    private String destinationAccount;

    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    @Digits(integer = 16, fraction = 2, message = "amount must have at most 2 decimal places")
    private BigDecimal amount;

    @NotBlank
    @Pattern(regexp = "[A-Za-z]{3}", message = "currency must be a 3-letter ISO code")
    private String currency;

    @NotBlank
    private String idempotencyKey;

    public CreatePaymentRequest() {
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }
}