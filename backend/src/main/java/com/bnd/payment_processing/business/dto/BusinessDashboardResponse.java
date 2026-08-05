package com.bnd.payment_processing.business.dto;

import com.bnd.payment_processing.payment.dto.PaymentResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Response body for {@code GET /api/business/dashboard} (spec.md Section 7.8).
 */
public record BusinessDashboardResponse(
        BigDecimal totalReceivedUsd,
        BigDecimal gstCollected,
        long totalInvoices,
        Map<String, Long> countByPaymentStatus,
        long pendingSettlements,
        long pendingRefundApprovals,
        List<PaymentResponse> recentPayments) {
}
