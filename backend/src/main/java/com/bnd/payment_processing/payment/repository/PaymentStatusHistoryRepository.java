package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.PaymentStatusHistory;

import java.util.List;
import java.util.UUID;

/**
 * Persistence contract for the append-only {@code payment_status_history} table.
 * Owner: M2 (Neha).
 */
public interface PaymentStatusHistoryRepository {

    PaymentStatusHistory insert(PaymentStatusHistory entry);

    /** Ordered oldest-first, per spec.md Section 10.4. */
    List<PaymentStatusHistory> findByPaymentIdOrderByChangedAtAsc(UUID paymentId);
}
