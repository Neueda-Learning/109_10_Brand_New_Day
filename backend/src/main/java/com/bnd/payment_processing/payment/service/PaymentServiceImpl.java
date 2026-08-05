package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidInvoiceStateException;
import com.bnd.payment_processing.common.exception.InvalidStatusTransitionException;
import com.bnd.payment_processing.common.exception.InvoiceNotFoundException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.invoice.model.Invoice;
import com.bnd.payment_processing.invoice.model.InvoiceStatus;
import com.bnd.payment_processing.invoice.repository.InvoiceRepository;
import com.bnd.payment_processing.invoice.service.InvoiceService;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentMapper;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentMethodType;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentStatusHistory;
import com.bnd.payment_processing.payment.model.SettlementStatus;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import com.bnd.payment_processing.payment.repository.PaymentStatusHistoryRepository;
import com.bnd.payment_processing.paymentmethod.model.PaymentMethod;
import com.bnd.payment_processing.paymentmethod.service.PaymentMethodService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Implementation of {@link PaymentService} (product.md Section 9.3 / Section 10.1).
 */
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String SYSTEM_TRIGGER = "SYSTEM";
    private static final String DEMO_TOKENIZER_PROVIDER = "DEMO_TOKENIZER";

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository paymentStatusHistoryRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceService invoiceService;
    private final PaymentMethodService paymentMethodService;
    private final FxConversionService fxConversionService;

    public PaymentServiceImpl(PaymentRepository paymentRepository,
                              PaymentStatusHistoryRepository paymentStatusHistoryRepository,
                              InvoiceRepository invoiceRepository,
                              InvoiceService invoiceService,
                              PaymentMethodService paymentMethodService,
                              FxConversionService fxConversionService) {
        this.paymentRepository = paymentRepository;
        this.paymentStatusHistoryRepository = paymentStatusHistoryRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceService = invoiceService;
        this.paymentMethodService = paymentMethodService;
        this.fxConversionService = fxConversionService;
    }

    @Override
    @Transactional
    public PaymentResponse createPayment(CreatePaymentRequest request) {
        Invoice invoice = invoiceRepository.findById(request.getInvoiceId())
                .orElseThrow(() -> new InvoiceNotFoundException(request.getInvoiceId()));

        if (!invoice.getCustomerId().equals(request.getCustomerId())) {
            throw new IllegalArgumentException(
                    "customerId " + request.getCustomerId() + " does not match invoice's customer " + invoice.getCustomerId());
        }

        if (invoice.getStatus() != InvoiceStatus.ISSUED && invoice.getStatus() != InvoiceStatus.PAYMENT_PENDING) {
            throw new InvalidInvoiceStateException(
                    "Invoice " + invoice.getId() + " is not payable in status " + invoice.getStatus());
        }

        if (!invoice.getCurrency().equalsIgnoreCase(request.getCurrency())) {
            throw new IllegalArgumentException(
                    "Payment currency " + request.getCurrency() + " must match invoice currency " + invoice.getCurrency());
        }

        PaymentMethodType methodType = parseEnum(PaymentMethodType.class, request.getPaymentMethodType(), "paymentMethodType");

        PaymentMethod paymentMethod = paymentMethodService.createForPayment(
                request.getCustomerId(), methodType, request.getMaskedIdentifier(), request.getTokenRef(), DEMO_TOKENIZER_PROVIDER);

        FxConversionResult fx = fxConversionService.convertToUsd(invoice.getTotalAmount(), request.getCurrency());

        Instant now = Instant.now();

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setInvoiceId(invoice.getId());
        payment.setCustomerId(request.getCustomerId());
        payment.setPaymentMethodId(paymentMethod.getId());
        payment.setIdempotencyKey(request.getIdempotencyKey());
        payment.setAmount(invoice.getTotalAmount());
        payment.setCurrency(request.getCurrency());
        payment.setExchangeRateId(fx.exchangeRateId());
        payment.setFxRate(fx.rate());
        payment.setUsdAmount(fx.usdAmount());
        payment.setStatus(PaymentStatus.CREATED);
        payment.setSettlementStatus(SettlementStatus.NOT_READY);
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

        // Best-effort - only actually changes the row when still ISSUED (spec.md
        // Section 9.1); a retried payment against an already PAYMENT_PENDING invoice
        // leaves it untouched.
        invoiceService.transitionStatusIfCurrent(invoice.getId(), InvoiceStatus.ISSUED, InvoiceStatus.PAYMENT_PENDING);

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

        // Settlement / invoice side-effects (spec.md Section 9.3): a payment reaching
        // COMPLETED marks its invoice PAID and starts settlement (PENDING); reaching
        // FAILED marks its invoice FAILED. Both are conditional updates, so they're
        // no-ops if the invoice already moved on for some other reason.
        if (nextStatus == PaymentStatus.COMPLETED) {
            invoiceService.transitionStatusIfCurrent(current.getInvoiceId(), InvoiceStatus.PAYMENT_PENDING, InvoiceStatus.PAID);
            paymentRepository.updateSettlementStatusIfCurrent(id, SettlementStatus.NOT_READY.name(), SettlementStatus.PENDING.name());
        } else if (nextStatus == PaymentStatus.FAILED) {
            invoiceService.transitionStatusIfCurrent(current.getInvoiceId(), InvoiceStatus.PAYMENT_PENDING, InvoiceStatus.FAILED);
        }

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

        List<PaymentStatusHistory> historyRecords = paymentStatusHistoryRepository
                .findByPaymentIdOrderByChangedAtAsc(id);

        return historyRecords.stream()
                .map(this::toHistoryEntry)
                .toList();
    }

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
    public Map<String, Object> searchPayments(Map<String, Object> filters, int page, int size) {
        Map<String, Object> validatedFilters = new LinkedHashMap<>();

        Object status = filters.get("status");
        if (status != null) {
            validatedFilters.put("status", parseEnum(PaymentStatus.class, status.toString(), "status").name());
        }

        Object settlementStatus = filters.get("settlementStatus");
        if (settlementStatus != null) {
            validatedFilters.put("settlementStatus",
                    parseEnum(SettlementStatus.class, settlementStatus.toString(), "settlementStatus").name());
        }

        Object currency = filters.get("currency");
        if (currency != null) {
            validatedFilters.put("currency", currency.toString().toUpperCase());
        }

        Object customerId = filters.get("customerId");
        if (customerId != null) {
            validatedFilters.put("customerId", parseUuid(customerId.toString(), "customerId"));
        }

        Object methodType = filters.get("methodType");
        if (methodType != null) {
            validatedFilters.put("methodType", parseEnum(PaymentMethodType.class, methodType.toString(), "methodType").name());
        }

        Object invoiceNumber = filters.get("invoiceNumber");
        if (invoiceNumber != null) {
            UUID invoiceId = invoiceRepository.findByInvoiceNumber(invoiceNumber.toString())
                    .map(Invoice::getId)
                    // No matching invoice: force zero results rather than ignoring the filter.
                    .orElse(new UUID(0L, 0L));
            validatedFilters.put("invoiceId", invoiceId);
        }

        if (filters.get("fromDate") instanceof LocalDate fromDate) {
            validatedFilters.put("fromDate", fromDate);
        }
        if (filters.get("toDate") instanceof LocalDate toDate) {
            validatedFilters.put("toDate", toDate);
        }

        List<Payment> results = paymentRepository.search(validatedFilters, page, size);
        long total = paymentRepository.countSearch(validatedFilters);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", results.stream().map(PaymentMapper::toResponse).toList());
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", total);
        response.put("totalPages", size == 0 ? 0 : (int) Math.ceil((double) total / size));
        return response;
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String fieldName) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
        }
    }

    private UUID parseUuid(String value, String fieldName) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid " + fieldName + ": " + value);
        }
    }
}
