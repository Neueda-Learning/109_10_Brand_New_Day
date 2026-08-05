package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business logic contract for payment creation and lifecycle operations
 * (product.md Section 10.1 / 10.2). Refund operations live in
 * {@code com.bnd.payment_processing.refund.service.RefundService}.
 */
public interface PaymentService {

    PaymentResponse createPayment(CreatePaymentRequest request);

    PaymentResponse getPayment(UUID id);

    PaymentResponse processTransition(UUID id, ProcessRequest request);

    List<PaymentHistoryEntry> getHistory(UUID id);

    Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size);
}

