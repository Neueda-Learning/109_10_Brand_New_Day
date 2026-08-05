package com.bnd.payment_processing.refund.dto;

import com.bnd.payment_processing.refund.model.Refund;

/**
 * Static mapper: {@link Refund} domain model -> {@link RefundResponse}
 * (same pattern as {@code payment.dto.PaymentMapper}).
 */
public final class RefundMapper {

    private RefundMapper() {
    }

    public static RefundResponse toResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getUsdAmount(),
                refund.getReason(),
                refund.getApprovalStatus().name(),
                refund.getStatus().name(),
                refund.getApprovedBy(),
                refund.getApprovedAt(),
                refund.getRejectionReason(),
                refund.getCreatedAt(),
                refund.getUpdatedAt());
    }
}
