package com.bnd.payment_processing.payment.service;

<<<<<<< Updated upstream
=======
import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidStatusTransitionException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
>>>>>>> Stashed changes
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
        return toResponse(updated);
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
    public PaymentResponse createRefund(UUID originalId, RefundRequest request) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }

    @Override
    public Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }
}
