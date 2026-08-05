package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.dto.PaymentInsightsResponse;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import com.bnd.payment_processing.payment.repository.PaymentAnalyticsRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Implementation of {@link PaymentAnalyticsService}.
 */
@Service
public class PaymentAnalyticsServiceImpl implements PaymentAnalyticsService {

    private final PaymentAnalyticsRepository paymentAnalyticsRepository;

    public PaymentAnalyticsServiceImpl(PaymentAnalyticsRepository paymentAnalyticsRepository) {
        this.paymentAnalyticsRepository = paymentAnalyticsRepository;
    }

    @Override
    public PaymentInsightsResponse getInsights(String status, String type, LocalDate fromDate, LocalDate toDate) {
        Map<String, Object> filters = new LinkedHashMap<>();
        if (status != null) {
            filters.put("status", parseEnum(PaymentStatus.class, status, "status").name());
        }
        if (type != null) {
            filters.put("type", parseEnum(PaymentType.class, type, "type").name());
        }
        if (fromDate != null) {
            filters.put("fromDate", fromDate);
        }
        if (toDate != null) {
            filters.put("toDate", toDate);
        }
        return paymentAnalyticsRepository.getInsights(filters);
    }

    // Same manual query-param validation pattern as PaymentServiceImpl.searchPayments (Section 10.3/10.10).
    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String rawValue, String paramName) {
        try {
            return Enum.valueOf(enumType, rawValue.toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid " + paramName + " value: " + rawValue);
        }
    }
}
