package com.bnd.payment_processing.refund.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Response body for refund endpoints (product.md Section 10.1 / 10.2).
 */
public record RefundResponse(
        UUID id,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        BigDecimal usdAmount,
        String reason,
        String approvalStatus,
        String status,
        String approvedBy,
        Instant approvedAt,
        String rejectionReason,
        Instant createdAt,
        Instant updatedAt) {
}
