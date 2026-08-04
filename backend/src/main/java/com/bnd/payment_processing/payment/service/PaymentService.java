package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic contract for all payment operations. Split by owner in
 * spec.md Section 9: M1 owns createPayment/getPayment, M2 owns
 * processTransition/getHistory, M3 owns createRefund (+ idempotency lookup
 * used inside createPayment), M4 owns searchPayments.
 */
public interface PaymentService {

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse getPayment(UUID id);

    PaymentResponse processTransition(UUID id, ProcessRequest request);

    List<PaymentHistoryEntry> getHistory(UUID id);

    PaymentResponse createRefund(UUID originalId, RefundRequest request);

    Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size);
}
