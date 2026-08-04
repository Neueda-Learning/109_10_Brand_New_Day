package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Payment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for the {@code payments} table.
 * Owner: M1 (Poornima) for create/get; M4 (Karuna) adds the query/filter method.
 */
public interface PaymentRepository {

    Payment insert(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Conditional update used by the status engine (spec.md Section 8.3):
     * updates status/updatedAt only if the row's current status still matches
     * {@code expectedCurrentStatus}. Returns the number of rows affected (0 or 1).
     */
    int updateStatusIfCurrent(UUID id, String expectedCurrentStatus, String newStatus, String errorCode);

    /**
     * Filter/search used by {@code GET /api/payments} (spec.md Section 10.3).
     * filters may include: status, type, sourceAccount, destinationAccount, fromDate, toDate.
     */
    List<Payment> search(Map<String, Object> filters, int page, int size);

    long countSearch(Map<String, Object> filters);

    /** Sum of amounts of all existing refund rows against the given original payment id. */
    java.math.BigDecimal sumRefundedAmount(UUID originalPaymentId);
}
