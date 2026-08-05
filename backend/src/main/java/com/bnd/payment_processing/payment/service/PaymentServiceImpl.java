package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.common.exception.InvalidStatusTransitionException;
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
import java.util.LinkedHashMap;
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
        // M1 validation rule (spec.md Section 9): sourceAccount must differ from
        // destinationAccount. IllegalArgumentException is mapped to 400
        // VALIDATION_ERROR by GlobalExceptionHandler.
        if (request.getSourceAccount().equals(request.getDestinationAccount())) {
            throw new IllegalArgumentException("sourceAccount and destinationAccount must be different");
        }

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
    @Transactional
    public PaymentResponse processTransition(UUID id, ProcessRequest request) {
        Payment current = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        PaymentStatus nextStatus = getNextStatus(current.getStatus(), request);

        validateTransition(current, nextStatus, request);

        String errorCode = null;
        if (nextStatus == PaymentStatus.FAILED) {
            errorCode = request.getErrorCode();
        }

        int rowsAffected = paymentRepository.updateStatusIfCurrent(
                id,
                current.getStatus().name(),
                nextStatus.name(),
                errorCode
        );

        if (rowsAffected == 0) {
            throw new InvalidStatusTransitionException(
                    "Payment status has changed since read; expected " + current.getStatus()
            );
        }

        Instant now = Instant.now();
        PaymentStatusHistory historyEntry = new PaymentStatusHistory();
        historyEntry.setId(UUID.randomUUID());
        historyEntry.setPaymentId(id);
        historyEntry.setFromStatus(current.getStatus());
        historyEntry.setToStatus(nextStatus);
        historyEntry.setChangedAt(now);
        historyEntry.setTriggeredBy("SYSTEM");
        historyEntry.setNote(request != null ? request.getNote() : null);
        paymentStatusHistoryRepository.insert(historyEntry);

        Payment updated = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));
        return PaymentMapper.toResponse(updated);
    }

    private PaymentStatus getNextStatus(PaymentStatus currentStatus, ProcessRequest request) {
        switch (currentStatus) {
            case CREATED:
                return PaymentStatus.VALIDATED;
            case VALIDATED:
                return PaymentStatus.SENT;
            case SENT:
                String target = (request != null && request.getTargetStatus() != null)
                        ? request.getTargetStatus()
                        : "COMPLETED";
                if ("COMPLETED".equals(target)) {
                    return PaymentStatus.COMPLETED;
                } else if ("FAILED".equals(target)) {
                    return PaymentStatus.FAILED;
                } else {
                    throw new InvalidStatusTransitionException(
                            "For SENT status, targetStatus must be 'COMPLETED' or 'FAILED', got: " + target
                    );
                }
            case COMPLETED:
            case FAILED:
                throw new InvalidStatusTransitionException(
                        "Cannot transition from terminal status " + currentStatus
                );
            default:
                throw new InvalidStatusTransitionException("Unknown status: " + currentStatus);
        }
    }

    private void validateTransition(Payment payment, PaymentStatus nextStatus, ProcessRequest request) {

        if (request != null && request.getTargetStatus() != null) {
            if (payment.getStatus() != PaymentStatus.SENT) {
                throw new InvalidStatusTransitionException(
                        "targetStatus can only be specified when current status is SENT, got: "
                                + payment.getStatus()
                );
            }
        }

        if (nextStatus == PaymentStatus.FAILED) {
            if (request == null || request.getErrorCode() == null || request.getErrorCode().isBlank()) {
                throw new IllegalArgumentException(
                        "errorCode is required when transitioning to FAILED status"
                );
            }
        }
    }

    @Override
    public List<PaymentHistoryEntry> getHistory(UUID id) {
        // Ensure payment exists (throw 404 if not)
        paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentNotFoundException(id));

        // Fetch and convert history records
        List<PaymentStatusHistory> historyRecords = paymentStatusHistoryRepository
                .findByPaymentIdOrderByChangedAtAsc(id);

        return historyRecords.stream()
                .map(this::toHistoryEntry)
                .toList();
    }

    /**
     * Convert a PaymentStatusHistory DB record to a DTO for API response.
     */
    private PaymentHistoryEntry toHistoryEntry(PaymentStatusHistory h) {
        PaymentHistoryEntry entry = new PaymentHistoryEntry();
        entry.setFromStatus(h.getFromStatus());
        entry.setToStatus(h.getToStatus());
        entry.setChangedAt(h.getChangedAt());
        entry.setTriggeredBy(h.getTriggeredBy());
        entry.setNote(h.getNote());
        return entry;
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
        Map<String, Object> validatedFilters = new LinkedHashMap<>();

        Object status = filters.get("status");
        if (status != null) {
            validatedFilters.put("status", parseEnum(PaymentStatus.class, status.toString(), "status").name());
        }

        Object type = filters.get("type");
        if (type != null) {
            validatedFilters.put("type", parseEnum(PaymentType.class, type.toString(), "type").name());
        }

        if (filters.get("sourceAccount") != null) {
            validatedFilters.put("sourceAccount", filters.get("sourceAccount"));
        }
        if (filters.get("destinationAccount") != null) {
            validatedFilters.put("destinationAccount", filters.get("destinationAccount"));
        }
        if (filters.get("fromDate") != null) {
            validatedFilters.put("fromDate", filters.get("fromDate"));
        }
        if (filters.get("toDate") != null) {
            validatedFilters.put("toDate", filters.get("toDate"));
        }

        List<PaymentResponse> content = paymentRepository.search(validatedFilters, page, size).stream()
                .map(PaymentMapper::toResponse)
                .toList();
        long totalElements = paymentRepository.countSearch(validatedFilters);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("content", content);
        result.put("page", page);
        result.put("size", size);
        result.put("totalElements", totalElements);
        return result;
    }

    // Query-param enums are validated here (not Bean Validation) since they're plain strings on a GET; invalid values
    // are surfaced as IllegalArgumentException pending M3's GlobalExceptionHandler VALIDATION_ERROR mapping (Section 10.7).
    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String rawValue, String paramName) {
        try {
            return Enum.valueOf(enumType, rawValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + paramName + " value: " + rawValue);
        }
    }
}