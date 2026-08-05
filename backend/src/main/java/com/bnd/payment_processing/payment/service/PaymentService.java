package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.ApproveRefundRequest;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.dto.RejectRefundRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic contract for all payment operations. Split by owner in
 * spec.md Section 9: M1 owns createPayment/getPayment, M2 owns
 * processTransition/getHistory, M3 owns createRefund/approveRefund/rejectRefund
 * (+ idempotency lookup used inside createPayment), M4 owns searchPayments.
 */
public interface PaymentService {

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse getPayment(UUID id);

    PaymentResponse processTransition(UUID id, ProcessRequest request);

    List<PaymentHistoryEntry> getHistory(UUID id);

    PaymentResponse createRefund(UUID originalId, RefundRequest request);

    /**
     * Approve a PENDING_APPROVAL refund so it can proceed through process()
     * (spec.md Section 10.8, added 2026-08-05).
     */
    PaymentResponse approveRefund(UUID refundId, ApproveRefundRequest request);

    /**
     * Reject a PENDING_APPROVAL refund; moves its status straight to FAILED
     * (spec.md Section 10.9, added 2026-08-05).
     */
    PaymentResponse rejectRefund(UUID refundId, RejectRefundRequest request);

    Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size);
}
