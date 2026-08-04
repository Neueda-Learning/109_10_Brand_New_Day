package com.bnd.payment_processing.payment.dto;

import com.bnd.payment_processing.payment.model.Payment;

/**
 * Converts the internal {@link Payment} domain model to the public
 * {@link PaymentResponse} shape (spec.md Section 10.1). Shared by
 * {@code PaymentServiceImpl} (normal responses) and
 * {@code GlobalExceptionHandler} (the duplicate-idempotency-key short-circuit,
 * spec.md Section 10.7), so the field mapping only lives in one place.
 */
public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setIdempotencyKey(payment.getIdempotencyKey());
        response.setSourceAccount(payment.getSourceAccount());
        response.setDestinationAccount(payment.getDestinationAccount());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setStatus(payment.getStatus());
        response.setErrorCode(payment.getErrorCode());
        response.setType(payment.getType());
        response.setOriginalPaymentId(payment.getOriginalPaymentId());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }
}
