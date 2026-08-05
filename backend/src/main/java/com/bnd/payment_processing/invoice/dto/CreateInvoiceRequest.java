package com.bnd.payment_processing.invoice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request body for {@code POST /api/invoices} (product.md Section 10.1).
 */
public class CreateInvoiceRequest {

    @NotNull
    private UUID customerId;

    @NotBlank
    private String productCode;

    @NotBlank
    private String currency;

    public CreateInvoiceRequest() {
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public void setCustomerId(UUID customerId) {
        this.customerId = customerId;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
