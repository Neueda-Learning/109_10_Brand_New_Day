package com.bnd.payment_processing.payment.controller;

import com.bnd.payment_processing.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code GET /api/payments} list/filter/search endpoint (product.md Section 10.1).
 * The old {@code /insights} aggregate endpoint was replaced by
 * {@code GET /api/business/dashboard} (see the {@code business} package).
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentQueryController {

    private final PaymentService paymentService;

    public PaymentQueryController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public Map<String, Object> searchPayments(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String settlementStatus,
            @RequestParam(required = false) String currency,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String invoiceNumber,
            @RequestParam(required = false) String methodType,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0, got: " + page);
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100, got: " + size);
        }

        Map<String, Object> filters = new LinkedHashMap<>();
        if (status != null) {
            filters.put("status", status);
        }
        if (settlementStatus != null) {
            filters.put("settlementStatus", settlementStatus);
        }
        if (currency != null) {
            filters.put("currency", currency);
        }
        if (customerId != null) {
            filters.put("customerId", customerId);
        }
        if (invoiceNumber != null) {
            filters.put("invoiceNumber", invoiceNumber);
        }
        if (methodType != null) {
            filters.put("methodType", methodType);
        }
        if (fromDate != null) {
            filters.put("fromDate", fromDate);
        }
        if (toDate != null) {
            filters.put("toDate", toDate);
        }
        return paymentService.searchPayments(filters, page, size);
    }
}
