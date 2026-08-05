package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.dto.PaymentInsightsResponse;

import java.util.Map;

/**
 * Read-only aggregate/analytics queries backing {@code GET /api/payments/insights}
 * (spec.md Section 10.10). Kept separate from {@link PaymentRepository} since it never
 * touches individual payment rows, only aggregates. Owner: M4 (Karuna).
 */
public interface PaymentAnalyticsRepository {

    /**
     * filters may include: status, type, fromDate, toDate (same subset accepted by
     * GET /api/payments/insights, spec.md Section 10.10).
     */
    PaymentInsightsResponse getInsights(Map<String, Object> filters);
}
