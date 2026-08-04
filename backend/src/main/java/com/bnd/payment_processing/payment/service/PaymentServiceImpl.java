package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentMapper;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link PaymentService}. Method bodies are stubs until
 * Phase 2 - see spec.md Section 9 for the per-module implementation checklist.
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String SYSTEM_TRIGGER = "SYSTEM";

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
        Instant now = Instant.now();

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setSourceAccount(request.getSourceAccount());
        payment.setDestinationAccount(request.getDestinationAccount());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setType(PaymentType.PAYMENT);
        payment.setOriginalPaymentId(null);
        payment.setCreatedAt(now);
        payment.setUpdatedAt(now);

        // Idempotency (spec.md Section 8.3): attempt the insert directly rather than
        // "check then insert" - the unique constraint on idempotency_key is the single
        // source of truth. On a duplicate key, re-fetch and let the caller (via
        // GlobalExceptionHandler) return the existing row as a 200 short-circuit
        // instead of creating a second row.
        try {
            paymentRepository.insert(payment);
        } catch (DuplicateKeyException e) {
            Payment existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
                    .orElseThrow(() -> e);
            throw new DuplicatePaymentException(existing);
        }

        PaymentStatusHistory initialHistory = new PaymentStatusHistory();
        initialHistory.setId(UUID.randomUUID());
        initialHistory.setPaymentId(payment.getId());
        initialHistory.setFromStatus(null);
        initialHistory.setToStatus(PaymentStatus.CREATED);
        initialHistory.setChangedAt(now);
        initialHistory.setTriggeredBy(SYSTEM_TRIGGER);
        initialHistory.setNote(null);
        paymentStatusHistoryRepository.insert(initialHistory);

        return PaymentMapper.toResponse(payment);
    }

    @Override
    public PaymentResponse getPayment(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentMapper.toResponse(payment);
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
    @Transactional
    public PaymentResponse createRefund(UUID originalId, RefundRequest request) {
        Payment original = paymentRepository.findById(originalId)
                .orElseThrow(() -> new PaymentNotFoundException(originalId));

        if (original.getType() != PaymentType.PAYMENT) {
            throw new InvalidRefundStateException(
                    "Payment " + originalId + " is a REFUND and cannot itself be refunded");
        }
        if (original.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidRefundStateException(
                    "Payment " + originalId + " must be COMPLETED to be refunded, was " + original.getStatus());
        }

        // Cumulative refund amount check (spec.md Section 8.1, rule 4) - must happen in
        // the same transaction as the insert below so two concurrent refund requests
        // can't both pass the check (spec.md Section 8.3).
        BigDecimal alreadyRefunded = paymentRepository.sumRefundedAmount(originalId);
        BigDecimal newTotal = alreadyRefunded.add(request.getAmount());
        if (newTotal.compareTo(original.getAmount()) > 0) {
            throw new InvalidRefundStateException(
                    "Refund amount " + request.getAmount() + " would exceed the refundable balance of payment "
                            + originalId + " (already refunded " + alreadyRefunded + " of " + original.getAmount() + ")");
        }

        Instant now = Instant.now();
        Payment refund = new Payment();
        refund.setId(UUID.randomUUID());
        // Refunds have no client-supplied idempotency key (RefundRequest doesn't carry
        // one) but the column is NOT NULL UNIQUE, so generate a synthetic one.
        refund.setIdempotencyKey("refund-" + refund.getId());
        // A refund reverses the money flow: the original payee now sends money back to
        // the original payer, so source/destination are swapped relative to the original.
        refund.setSourceAccount(original.getDestinationAccount());
        refund.setDestinationAccount(original.getSourceAccount());
        refund.setAmount(request.getAmount());
        refund.setCurrency(original.getCurrency());
        refund.setStatus(PaymentStatus.CREATED);
        refund.setType(PaymentType.REFUND);
        refund.setOriginalPaymentId(original.getId());
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);

        paymentRepository.insert(refund);

        PaymentStatusHistory initialHistory = new PaymentStatusHistory();
        initialHistory.setId(UUID.randomUUID());
        initialHistory.setPaymentId(refund.getId());
        initialHistory.setFromStatus(null);
        initialHistory.setToStatus(PaymentStatus.CREATED);
        initialHistory.setChangedAt(now);
        initialHistory.setTriggeredBy(SYSTEM_TRIGGER);
        initialHistory.setNote(request.getReason());
        paymentStatusHistoryRepository.insert(initialHistory);

        return PaymentMapper.toResponse(refund);
    }

    @Override
    public Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }
}
