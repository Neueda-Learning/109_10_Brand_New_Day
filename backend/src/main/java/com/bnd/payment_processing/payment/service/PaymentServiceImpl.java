package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.common.exception.InvalidStatusTransitionException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.common.exception.RefundNotApprovedException;
import com.bnd.payment_processing.payment.dto.ApproveRefundRequest;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentMapper;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.dto.RejectRefundRequest;
import com.bnd.payment_processing.payment.model.ApprovalStatus;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentMethod;
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
        // paymentMethod (spec.md Section 10.1, v2.2): optional in the request, defaults
        // to BANK_TRANSFER server-side if omitted. Reuses the existing parseEnum() helper.
        payment.setPaymentMethod(request.getPaymentMethod() == null || request.getPaymentMethod().isBlank()
                ? PaymentMethod.BANK_TRANSFER
                : parseEnum(PaymentMethod.class, request.getPaymentMethod(), "paymentMethod"));
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

        // Refund approval gate (spec.md Section 8.1 rule 6, added 2026-08-05): a REFUND
        // row can never advance past CREATED until a business user has approved it via
        // POST /refund/approve. Guarded strictly on type == REFUND so PAYMENT-type
        // transitions (M1/M2) are completely unaffected by this check.
        if (current.getType() == PaymentType.REFUND
                && current.getStatus() == PaymentStatus.CREATED
                && current.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw new RefundNotApprovedException(
                    "Refund " + id + " cannot be processed until it has been approved (current approvalStatus: "
                            + current.getApprovalStatus() + ")");
        }

        PaymentStatus nextStatus = getNextStatus(current.getStatus(), request);
        validateTransition(current, nextStatus, request);

        String errorCode = null;
        if (nextStatus == PaymentStatus.FAILED) {
            errorCode = request.getErrorCode().trim();
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
        historyEntry.setTriggeredBy(SYSTEM_TRIGGER);

        String requestNote = (request != null && request.getNote() != null && !request.getNote().isBlank())
                ? request.getNote().trim()
                : null;
        if (nextStatus == PaymentStatus.FAILED) {
            historyEntry.setNote(requestNote == null ? errorCode : requestNote + " | errorCode=" + errorCode);
        } else {
            historyEntry.setNote(requestNote);
        }

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
        // Locked read (spec.md Section 8.3, added 2026-08-05): take a row lock on the
        // original payment before computing the cumulative refunded total below, so two
        // near-simultaneous refund requests against the same payment can't both pass the
        // amount check before either commits.
        Payment original = paymentRepository.findByIdForUpdate(originalId)
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
        refund.setIdempotencyKey(request.getIdempotencyKey() != null && !request.getIdempotencyKey().isBlank()
                ? request.getIdempotencyKey()
                : "refund-" + refund.getId());
        // A refund reverses the money flow: the original payee now sends money back to
        // the original payer, so source/destination are swapped relative to the original.
        refund.setSourceAccount(original.getDestinationAccount());
        refund.setDestinationAccount(original.getSourceAccount());
        refund.setAmount(request.getAmount());
        refund.setCurrency(original.getCurrency());
        refund.setStatus(PaymentStatus.CREATED);
        refund.setType(PaymentType.REFUND);
        refund.setOriginalPaymentId(original.getId());
        // Approval gate (spec.md Section 8.1 rule 6, added 2026-08-05): every new refund
        // starts PENDING_APPROVAL and cannot advance past CREATED until approved.
        refund.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        refund.setPaymentMethod(original.getPaymentMethod() == null ? PaymentMethod.BANK_TRANSFER : original.getPaymentMethod());
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);

        // Refund idempotency (spec.md Section 10.6, added 2026-08-05): mirrors the same
        // duplicate-key-catch-and-refetch short-circuit pattern used by createPayment(),
        // so a double-submitted refund request can't create two rows.
        try {
            paymentRepository.insert(refund);
        } catch (DuplicateKeyException e) {
            Payment existing = paymentRepository.findByIdempotencyKey(refund.getIdempotencyKey())
                    .orElseThrow(() -> e);
            throw new DuplicatePaymentException(existing);
        }

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
    @Transactional
    public PaymentResponse approveRefund(UUID refundId, ApproveRefundRequest request) {
        Payment refund = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));

        // Conditional update (spec.md Section 10.8, added 2026-08-05): only applies when
        // type=REFUND and approval_status=PENDING_APPROVAL. A 0-row result means the
        // refund is not a REFUND row, or was already approved/rejected.
        int rowsAffected = paymentRepository.approveRefund(refundId, request.getApprovedBy(), Instant.now());
        if (rowsAffected == 0) {
            throw new RefundNotApprovedException(
                    "Refund " + refundId + " cannot be approved (must be type=REFUND with approvalStatus=PENDING_APPROVAL, was type="
                            + refund.getType() + ", approvalStatus=" + refund.getApprovalStatus() + ")");
        }

        Payment updated = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));
        return PaymentMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public PaymentResponse rejectRefund(UUID refundId, RejectRefundRequest request) {
        Payment refund = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));

        // Conditional update (spec.md Section 10.9, added 2026-08-05): only applies when
        // type=REFUND and approval_status=PENDING_APPROVAL; also moves status straight to
        // FAILED with error_code=REFUND_REJECTED in the same atomic update.
        int rowsAffected = paymentRepository.rejectRefund(refundId, request.getReason());
        if (rowsAffected == 0) {
            throw new RefundNotApprovedException(
                    "Refund " + refundId + " cannot be rejected (must be type=REFUND with approvalStatus=PENDING_APPROVAL, was type="
                            + refund.getType() + ", approvalStatus=" + refund.getApprovalStatus() + ")");
        }

        Instant now = Instant.now();
        PaymentStatusHistory historyEntry = new PaymentStatusHistory();
        historyEntry.setId(UUID.randomUUID());
        historyEntry.setPaymentId(refundId);
        historyEntry.setFromStatus(PaymentStatus.CREATED);
        historyEntry.setToStatus(PaymentStatus.FAILED);
        historyEntry.setChangedAt(now);
        historyEntry.setTriggeredBy(request.getRejectedBy());
        historyEntry.setNote("REFUND_REJECTED: " + request.getReason());
        paymentStatusHistoryRepository.insert(historyEntry);

        Payment updated = paymentRepository.findById(refundId)
                .orElseThrow(() -> new PaymentNotFoundException(refundId));
        return PaymentMapper.toResponse(updated);
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