package com.bnd.payment_processing.payment.controller;

import com.bnd.payment_processing.payment.service.PaymentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * {@code GET /api/payments} list/filter/search endpoint (spec.md Section 10.3).
 * Owner: M4 (Karuna).
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
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String sourceAccount,
            @RequestParam(required = false) String destinationAccount,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }
}
