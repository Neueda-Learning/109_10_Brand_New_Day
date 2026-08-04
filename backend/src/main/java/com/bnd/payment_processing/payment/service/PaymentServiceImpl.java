package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import com.bnd.payment_processing.payment.repository.PaymentStatusHistoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link PaymentService}. Method bodies are stubs until
 * Phase 2 - see spec.md Section 9 for the per-module implementation checklist.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                               PaymentStatusHistoryRepository paymentStatusHistoryRepository) {
        this.paymentRepository = paymentRepository;
        this.paymentStatusHistoryRepository = paymentStatusHistoryRepository;
    }

    @Override
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M1)");
    }

    @Override
    public PaymentResponse getPayment(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M1)");
    }

    @Override
    public PaymentResponse processTransition(UUID id, ProcessRequest request) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }

    @Override
    public List<PaymentHistoryEntry> getHistory(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }

    @Override
    public PaymentResponse createRefund(UUID originalId, RefundRequest request) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }

    @Override
    public Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }
}
