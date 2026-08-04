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
import java.util.LinkedHashMap;
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
                .map(this::toResponse)
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

    private PaymentResponse toResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setIdempotencyKey(payment.getIdempotencyKey());
        response.setSourceAccount(payment.getSourceAccount());
        response.setDestinationAccount(payment.getDestinationAccount());
        response.setAmount(payment.getAmount());
        response.setCurrency(payment.getCurrency());
        response.setStatus(payment.getStatus());
        response.setErrorCode(payment.getErrorCode());
        response.setType(payment.getType());
        response.setOriginalPaymentId(payment.getOriginalPaymentId());
        response.setCreatedAt(payment.getCreatedAt());
        response.setUpdatedAt(payment.getUpdatedAt());
        return response;
    }
}