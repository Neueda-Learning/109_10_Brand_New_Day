package com.bnd.payment_processing.payment.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body shape shared by every endpoint that returns a payment
 * (product.md Section 10.1 / 10.2).
 */
public record PaymentResponse(
        UUID id,
        UUID invoiceId,
        UUID customerId,
        UUID paymentMethodId,
        String idempotencyKey,
        BigDecimal amount,
        String currency,
        UUID exchangeRateId,
        BigDecimal fxRate,
        BigDecimal usdAmount,
        String status,
        String settlementStatus,
        String errorCode,
        Instant createdAt,
        Instant updatedAt) {
}

