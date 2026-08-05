package com.bnd.payment_processing.refund.service;

import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.common.exception.RefundNotFoundException;
import com.bnd.payment_processing.invoice.model.InvoiceStatus;
import com.bnd.payment_processing.invoice.service.InvoiceService;
import com.bnd.payment_processing.payment.model.ApprovalStatus;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import com.bnd.payment_processing.payment.service.FxConversionResult;
import com.bnd.payment_processing.payment.service.FxConversionService;
import com.bnd.payment_processing.refund.dto.ApproveRefundRequest;
import com.bnd.payment_processing.refund.dto.CreateRefundRequest;
import com.bnd.payment_processing.refund.dto.RefundMapper;
import com.bnd.payment_processing.refund.dto.RefundResponse;
import com.bnd.payment_processing.refund.dto.RejectRefundRequest;
import com.bnd.payment_processing.refund.model.Refund;
import com.bnd.payment_processing.refund.model.RefundStatus;
import com.bnd.payment_processing.refund.repository.RefundRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefundServiceImpl implements RefundService {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final InvoiceService invoiceService;
    private final FxConversionService fxConversionService;

    public RefundServiceImpl(RefundRepository refundRepository,
                             PaymentRepository paymentRepository,
                             InvoiceService invoiceService,
                             FxConversionService fxConversionService) {
        this.refundRepository = refundRepository;
        this.paymentRepository = paymentRepository;
        this.invoiceService = invoiceService;
        this.fxConversionService = fxConversionService;
    }

    @Override
    @Transactional
    public RefundResponse createRefund(UUID paymentId, CreateRefundRequest request) {
        // Locked read (spec.md Section 8.3 pattern): take a row lock on the payment
        // before computing the cumulative refunded total below, so two near-simultaneous
        // refund requests against the same payment can't both pass the amount check.
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new InvalidRefundStateException(
                    "Payment " + paymentId + " must be COMPLETED to be refunded, was " + payment.getStatus());
        }

        BigDecimal alreadyRefunded = refundRepository.sumActiveAmountByPaymentId(paymentId);
        BigDecimal newTotal = alreadyRefunded.add(request.getAmount());
        if (newTotal.compareTo(payment.getAmount()) > 0) {
            throw new InvalidRefundStateException(
                    "Refund amount " + request.getAmount() + " would exceed the refundable balance of payment "
                            + paymentId + " (already refunded " + alreadyRefunded + " of " + payment.getAmount() + ")");
        }

        FxConversionResult fx = fxConversionService.convertToUsd(request.getAmount(), payment.getCurrency());

        Instant now = Instant.now();
        Refund refund = new Refund();
        refund.setId(UUID.randomUUID());
        refund.setPaymentId(paymentId);
        refund.setAmount(request.getAmount());
        refund.setCurrency(payment.getCurrency());
        refund.setUsdAmount(fx.usdAmount());
        refund.setReason(request.getReason());
        refund.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setCreatedAt(now);
        refund.setUpdatedAt(now);

        refundRepository.insert(refund);

        // Best-effort (spec.md Section 9.1 refund path PAID -> REFUND_REQUESTED): only
        // actually changes the row when still PAID.
        invoiceService.transitionStatusIfCurrent(payment.getInvoiceId(), InvoiceStatus.PAID, InvoiceStatus.REFUND_REQUESTED);

        return RefundMapper.toResponse(refund);
    }

    @Override
    public RefundResponse getRefund(UUID id) {
        Refund refund = refundRepository.findById(id).orElseThrow(() -> new RefundNotFoundException(id));
        return RefundMapper.toResponse(refund);
    }

    @Override
    @Transactional
    public RefundResponse approveRefund(UUID id, ApproveRefundRequest request) {
        Refund refund = refundRepository.findById(id).orElseThrow(() -> new RefundNotFoundException(id));

        // Conditional update: only applies when approval_status = PENDING_APPROVAL.
        int rowsAffected = refundRepository.approve(id, request.getApprovedBy(), Instant.now());
        if (rowsAffected == 0) {
            throw new InvalidRefundStateException(
                    "Refund " + id + " cannot be approved (must be PENDING_APPROVAL, was " + refund.getApprovalStatus() + ")");
        }

        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentId()));
        // Best-effort (spec.md Section 9.1 refund path REFUND_REQUESTED -> REFUNDED).
        invoiceService.transitionStatusIfCurrent(payment.getInvoiceId(), InvoiceStatus.REFUND_REQUESTED, InvoiceStatus.REFUNDED);

        Refund updated = refundRepository.findById(id).orElseThrow(() -> new RefundNotFoundException(id));
        return RefundMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public RefundResponse rejectRefund(UUID id, RejectRefundRequest request) {
        Refund refund = refundRepository.findById(id).orElseThrow(() -> new RefundNotFoundException(id));

        int rowsAffected = refundRepository.reject(id, request.getReason());
        if (rowsAffected == 0) {
            throw new InvalidRefundStateException(
                    "Refund " + id + " cannot be rejected (must be PENDING_APPROVAL, was " + refund.getApprovalStatus() + ")");
        }

        Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new PaymentNotFoundException(refund.getPaymentId()));
        // Rejected refund reverts the invoice back to PAID (spec.md Section 9.1).
        invoiceService.transitionStatusIfCurrent(payment.getInvoiceId(), InvoiceStatus.REFUND_REQUESTED, InvoiceStatus.PAID);

        Refund updated = refundRepository.findById(id).orElseThrow(() -> new RefundNotFoundException(id));
        return RefundMapper.toResponse(updated);
    }
}
