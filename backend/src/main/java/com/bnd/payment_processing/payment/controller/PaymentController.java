package com.bnd.payment_processing.payment.controller;

import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Payment creation and lifecycle endpoints (product.md Section 10.1 / 10.2).
 * Refund creation/approval/rejection live in
 * {@code com.bnd.payment_processing.refund.controller.RefundController}.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createPayment(request);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable UUID id) {
        return paymentService.getPayment(id);
    }

    @PostMapping("/{id}/process")
    public PaymentResponse processTransition(@PathVariable UUID id, @RequestBody(required = false) ProcessRequest request) {
        return paymentService.processTransition(id, request);
    }

    @GetMapping("/{id}/history")
    public List<PaymentHistoryEntry> getHistory(@PathVariable UUID id) {
        return paymentService.getHistory(id);
    }
}
