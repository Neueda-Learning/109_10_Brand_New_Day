package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentStatusHistory;
import com.bnd.payment_processing.payment.model.PaymentType;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import com.bnd.payment_processing.payment.repository.PaymentStatusHistoryRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of {@link PaymentService}. M1 (createPayment/getPayment) is
 * implemented below; M2/M3/M4 methods remain stubs until their Phase 2 work.
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
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        if (request.getSourceAccount().equals(request.getDestinationAccount())) {
            throw new IllegalArgumentException("sourceAccount and destinationAccount must differ");
        }

        // Idempotency short-circuit (rule owned by M3, but the create path must call it).
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            throw new DuplicatePaymentException(existing.get());
        }

        Instant now = Instant.now();
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setSourceAccount(request.getSourceAccount());
        payment.setDestinationAccount(request.getDestinationAccount());
        payment.setAmount(request.getAmount().setScale(2, RoundingMode.UNNECESSARY));
        payment.setCurrency(request.getCurrency().toUpperCase());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setType(PaymentType.PAYMENT);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        try {
            paymentRepository.insert(payment);
        } catch (DuplicateKeyException e) {
            // Race: another request inserted the same idempotency_key between check and insert.
            Payment race = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> e);
            throw new DuplicatePaymentException(race);
        }

        PaymentStatusHistory history = new PaymentStatusHistory();
        history.setId(UUID.randomUUID());
        history.setPaymentId(payment.getId());
        history.setFromStatus(null);
        history.setToStatus(PaymentStatus.CREATED);
        history.setChangedAt(now);
        history.setTriggeredBy("SYSTEM");
        paymentStatusHistoryRepository.insert(history);

        return toResponse(payment);
    }

    @Override
    public PaymentResponse getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return toResponse(payment);
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

    private PaymentResponse toResponse(Payment p) {
        PaymentResponse r = new PaymentResponse();
        r.setId(p.getId());
        r.setIdempotencyKey(p.getIdempotencyKey());
        r.setSourceAccount(p.getSourceAccount());
        r.setDestinationAccount(p.getDestinationAccount());
        r.setAmount(p.getAmount());
        r.setCurrency(p.getCurrency());
        r.setStatus(p.getStatus());
        r.setErrorCode(p.getErrorCode());
        r.setType(p.getType());
        r.setOriginalPaymentId(p.getOriginalPaymentId());
        r.setCreatedAt(p.getCreatedAt());
        r.setUpdatedAt(p.getUpdatedAt());
        return r;
    }
}