package com.bnd.payment_processing.invoice.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for invoice endpoints (product.md Section 10.1).
 */
public record InvoiceResponse(
        UUID id,
        String invoiceNumber,
        UUID customerId,
        String productName,
        String productCode,
        int creditUnits,
        BigDecimal subtotalAmount,
        BigDecimal gstAmount,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
