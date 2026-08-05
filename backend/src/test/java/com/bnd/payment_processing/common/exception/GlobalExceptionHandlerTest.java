package com.bnd.payment_processing.common.exception;

import com.bnd.payment_processing.payment.controller.PaymentController;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import com.bnd.payment_processing.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests proving {@link GlobalExceptionHandler} maps every exception to the
 * exact {@code ErrorResponse} shape / HTTP status from spec.md Section 10.7, and that
 * the DuplicatePaymentException short-circuit returns 200 with the plain payment body.
 * {@link PaymentService} is mocked - no database needed.
 */
@WebMvcTest(PaymentController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    @Test
    void paymentNotFound_returns404WithErrorCode() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.getPayment(id)).thenThrow(new PaymentNotFoundException(id));

        mockMvc.perform(get("/api/payments/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/payments/" + id));
    }

    @Test
    void invalidStatusTransition_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.processTransition(eq(id), any()))
                .thenThrow(new InvalidStatusTransitionException("payment is already in a terminal state"));

        mockMvc.perform(post("/api/payments/{id}/process", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_STATUS_TRANSITION"));
    }

    @Test
    void invalidRefundState_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.createRefund(eq(id), any()))
                .thenThrow(new InvalidRefundStateException("original payment is not COMPLETED"));

        mockMvc.perform(post("/api/payments/{id}/refund", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 10.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFUND_STATE"));
    }

    @Test
    void duplicatePayment_shortCircuitsWith200AndPlainPaymentBody() throws Exception {
        Payment existing = new Payment();
        existing.setId(UUID.randomUUID());
        existing.setIdempotencyKey("dup-key");
        existing.setSourceAccount("ACC-1");
        existing.setDestinationAccount("ACC-2");
        existing.setAmount(new BigDecimal("50.00"));
        existing.setCurrency("INR");
        existing.setStatus(PaymentStatus.CREATED);
        existing.setType(PaymentType.PAYMENT);
        existing.setCreatedAt(Instant.now());
        existing.setUpdatedAt(Instant.now());

        when(paymentService.createPayment(any())).thenThrow(new DuplicatePaymentException(existing));

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccount\":\"ACC-1\",\"destinationAccount\":\"ACC-2\","
                                + "\"amount\":50.00,\"currency\":\"INR\",\"idempotencyKey\":\"dup-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(existing.getId().toString()))
                .andExpect(jsonPath("$.idempotencyKey").value("dup-key"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    @Test
    void validationFailure_returns400WithValidationErrorCode() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sourceAccount\":\"\",\"destinationAccount\":\"ACC-2\","
                                + "\"amount\":-5,\"currency\":\"INR\",\"idempotencyKey\":\"k\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"));
    }

    // Added 2026-08-05 (spec.md Section 8.1 rule 6 / Section 10.8-10.9, v2.2)
    @Test
    void refundNotApproved_onProcess_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.processTransition(eq(id), any()))
                .thenThrow(new RefundNotApprovedException("refund is not yet approved"));

        mockMvc.perform(post("/api/payments/{id}/process", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_NOT_APPROVED"));
    }

    @Test
    void refundNotApproved_onApprove_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.approveRefund(eq(id), any()))
                .thenThrow(new RefundNotApprovedException("refund already approved or rejected"));

        mockMvc.perform(post("/api/payments/{id}/refund/approve", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedBy\":\"business-user-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_NOT_APPROVED"));
    }

    @Test
    void refundNotApproved_onReject_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(paymentService.rejectRefund(eq(id), any()))
                .thenThrow(new RefundNotApprovedException("refund already approved or rejected"));

        mockMvc.perform(post("/api/payments/{id}/refund/reject", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectedBy\":\"business-user-2\",\"reason\":\"test\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("REFUND_NOT_APPROVED"));
    }
}
