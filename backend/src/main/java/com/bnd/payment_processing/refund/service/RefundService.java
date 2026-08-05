package com.bnd.payment_processing.refund.service;

import com.bnd.payment_processing.refund.dto.ApproveRefundRequest;
import com.bnd.payment_processing.refund.dto.CreateRefundRequest;
import com.bnd.payment_processing.refund.dto.RefundResponse;
import com.bnd.payment_processing.refund.dto.RejectRefundRequest;

import java.util.UUID;

/**
 * Business logic contract for refund creation and approval workflow
 * (product.md Section 9.4 / 10.1 / 10.2).
 */
public interface RefundService {

    RefundResponse createRefund(UUID paymentId, CreateRefundRequest request);

    RefundResponse getRefund(UUID id);

    RefundResponse approveRefund(UUID id, ApproveRefundRequest request);

    RefundResponse rejectRefund(UUID id, RejectRefundRequest request);
}
