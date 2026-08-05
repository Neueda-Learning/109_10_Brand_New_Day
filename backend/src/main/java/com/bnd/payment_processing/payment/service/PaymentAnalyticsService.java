package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.PaymentInsightsResponse;

import java.time.LocalDate;

/**
 * Business logic for {@code GET /api/payments/insights} (spec.md Section 10.10).
 * Kept separate from {@link PaymentService} since it never touches individual
 * payment rows. Owner: M4 (Karuna).
 */
public interface PaymentAnalyticsService {

    PaymentInsightsResponse getInsights(String status, String type, LocalDate fromDate, LocalDate toDate);
}
