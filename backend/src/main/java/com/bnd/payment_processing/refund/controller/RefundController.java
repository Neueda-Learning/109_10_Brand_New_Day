package com.bnd.payment_processing.refund.controller;

import com.bnd.payment_processing.refund.dto.ApproveRefundRequest;
import com.bnd.payment_processing.refund.dto.CreateRefundRequest;
import com.bnd.payment_processing.refund.dto.RefundResponse;
import com.bnd.payment_processing.refund.dto.RejectRefundRequest;
import com.bnd.payment_processing.refund.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Refund creation is nested under {@code /api/payments/{paymentId}/refund}
 * (product.md Section 10.1); approve/reject live under {@code /api/refunds}
 * (product.md Section 10.2).
 */
@RestController
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/api/payments/{paymentId}/refund")
    @ResponseStatus(HttpStatus.CREATED)
    public RefundResponse createRefund(@PathVariable UUID paymentId, @Valid @RequestBody CreateRefundRequest request) {
        return refundService.createRefund(paymentId, request);
    }

    @GetMapping("/api/refunds/{id}")
    public RefundResponse getRefund(@PathVariable UUID id) {
        return refundService.getRefund(id);
    }

    @PostMapping("/api/refunds/{id}/approve")
    public RefundResponse approveRefund(@PathVariable UUID id, @Valid @RequestBody ApproveRefundRequest request) {
        return refundService.approveRefund(id, request);
    }

    @PostMapping("/api/refunds/{id}/reject")
    public RefundResponse rejectRefund(@PathVariable UUID id, @Valid @RequestBody RejectRefundRequest request) {
        return refundService.rejectRefund(id, request);
    }
}
