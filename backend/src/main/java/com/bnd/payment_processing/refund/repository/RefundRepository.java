package com.bnd.payment_processing.refund.repository;

import com.bnd.payment_processing.refund.model.Refund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for the {@code refunds} table (spec.md Section 7.7).
 */
public interface RefundRepository {

    Refund insert(Refund refund);

    Optional<Refund> findById(UUID id);

    List<Refund> findByPaymentId(UUID paymentId);

    /**
     * Sum of amounts of all refunds against the given payment that are not
     * REJECTED/FAILED (used for the cumulative-refund-cap check).
     */
    BigDecimal sumActiveAmountByPaymentId(UUID paymentId);

    /**
     * Conditional approve: only applies when approval_status = PENDING_APPROVAL.
     * Moves approval_status -> APPROVED and status -> COMPLETED in one update
     * (this phase auto-processes an approved refund immediately, product.md
     * Section 9.4). Returns the number of rows affected (0 or 1).
     */
    int approve(UUID id, String approvedBy, java.time.Instant approvedAt);

    /**
     * Conditional reject: only applies when approval_status = PENDING_APPROVAL.
     * Moves approval_status -> REJECTED and status -> REJECTED in one update.
     * Returns the number of rows affected (0 or 1).
     */
    int reject(UUID id, String rejectionReason);
}
