package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import com.bnd.payment_processing.payment.repository.PaymentStatusHistoryRepository;
import org.springframework.stereotype.Service;

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
