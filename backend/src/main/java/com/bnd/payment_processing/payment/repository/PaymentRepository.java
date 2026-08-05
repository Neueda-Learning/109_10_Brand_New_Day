package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Payment;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for the {@code payments} table (spec.md Section 7.5).
 */
public interface PaymentRepository {

    Payment insert(Payment payment);

    Optional<Payment> findById(UUID id);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Same lookup as {@link #findById(UUID)} but takes a row lock (SELECT ... FOR
     * UPDATE) so it must be called inside an existing transaction.
     */
    Optional<Payment> findByIdForUpdate(UUID id);

    /**
     * Conditional update used by the status engine: updates status/errorCode/
     * updatedAt only if the row's current status still matches
     * {@code expectedCurrentStatus}. Returns the number of rows affected (0 or 1).
     */
    int updateStatusIfCurrent(UUID id, String expectedCurrentStatus, String newStatus, String errorCode);

    /**
     * Conditional settlement-status update, mirroring {@link #updateStatusIfCurrent}
     * but for the independent {@code settlement_status} column (spec.md Section 9.3).
     */
    int updateSettlementStatusIfCurrent(UUID id, String expectedCurrentSettlementStatus, String newSettlementStatus);

    /**
     * Filter/search used by {@code GET /api/payments} (product.md Section 10.2).
     * filters may include: status, settlementStatus, currency, customerId,
     * invoiceId, methodType, fromDate, toDate.
     */
    List<Payment> search(Map<String, Object> filters, int page, int size);

    long countSearch(Map<String, Object> filters);
}
