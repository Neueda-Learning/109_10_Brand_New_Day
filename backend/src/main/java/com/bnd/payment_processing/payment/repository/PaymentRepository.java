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
     * Same lookup as {@link #findById(UUID)} but takes a row lock (SELECT ... FOR
     * UPDATE) so it must be called inside an existing transaction. Used by
     * refund creation (spec.md Section 8.3) to close the race window where two
     * concurrent refund requests against the same original payment could both
     * pass the cumulative-amount check before either commits.
     */
    Optional<Payment> findByIdForUpdate(UUID id);

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

    /**
     * Conditional approve (spec.md Section 10.8, added 2026-08-05): only applies when
     * the row is {@code type = REFUND} and {@code approval_status = PENDING_APPROVAL}.
     * Returns the number of rows affected (0 or 1).
     */
    int approveRefund(UUID id, String approvedBy, java.time.Instant approvedAt);

    /**
     * Conditional reject (spec.md Section 10.9, added 2026-08-05): only applies when
     * the row is {@code type = REFUND} and {@code approval_status = PENDING_APPROVAL}.
     * Also moves {@code status} straight to {@code FAILED} with
     * {@code error_code = 'REFUND_REJECTED'} in the same conditional update.
     * Returns the number of rows affected (0 or 1).
     */
    int rejectRefund(UUID id, String rejectionReason);
}
