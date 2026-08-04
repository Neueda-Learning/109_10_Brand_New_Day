package com.bnd.payment_processing.payment.controller;

import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentHistoryEntry;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Endpoints owned by M1 (create/get), M2 (process/history) and M3 (refund) -
 * spec.md Section 9. Kept in a single controller since they all operate on
 * the same {@code /api/payments} resource path.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    // --- M1 ---

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody CreatePaymentRequest request) {
        return paymentService.createPayment(request);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable UUID id) {
        return paymentService.getPayment(id);
    }

    // --- M2 ---

    @PostMapping("/{id}/process")
    public PaymentResponse processTransition(@PathVariable UUID id, @RequestBody(required = false) ProcessRequest request) {
        return paymentService.processTransition(id, request);
    }

    @GetMapping("/{id}/history")
    public List<PaymentHistoryEntry> getHistory(@PathVariable UUID id) {
        return paymentService.getHistory(id);
    }

    // --- M3 ---

    @PostMapping("/{id}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createRefund(@PathVariable UUID id, @Valid @RequestBody RefundRequest request) {
        return paymentService.createRefund(id, request);
    }
}
