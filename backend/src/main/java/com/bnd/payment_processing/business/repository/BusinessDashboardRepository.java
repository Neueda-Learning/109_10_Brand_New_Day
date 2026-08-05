package com.bnd.payment_processing.business.repository;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Read-only aggregate queries backing {@code GET /api/business/dashboard}
 * (spec.md Section 7.8). Deliberately separate from {@code PaymentRepository}/
 * {@code InvoiceRepository}/{@code RefundRepository} since these are cross-table
 * aggregates, not single-entity CRUD.
 */
public interface BusinessDashboardRepository {

    BigDecimal sumCompletedUsdAmount();

    BigDecimal sumGstCollected();

    long countInvoices();

    Map<String, Long> countPaymentsByStatus();

    long countPendingSettlements();

    long countPendingRefundApprovals();
}
