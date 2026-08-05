package com.bnd.payment_processing.payment.dto;

import com.bnd.payment_processing.payment.model.Payment;

/**
 * Converts the internal {@link Payment} domain model to the public
 * {@link PaymentResponse} shape (product.md Section 10.1). Shared by
 * {@code PaymentServiceImpl} (normal responses) and
 * {@code GlobalExceptionHandler} (the duplicate-idempotency-key short-circuit),
 * so the field mapping only lives in one place.
 */
public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getInvoiceId(),
                payment.getCustomerId(),
                payment.getPaymentMethodId(),
                payment.getIdempotencyKey(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getExchangeRateId(),
                payment.getFxRate(),
                payment.getUsdAmount(),
                payment.getStatus().name(),
                payment.getSettlementStatus().name(),
                payment.getErrorCode(),
                payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}

