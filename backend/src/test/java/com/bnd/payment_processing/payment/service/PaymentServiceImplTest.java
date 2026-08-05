package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.common.exception.InvalidStatusTransitionException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.common.exception.RefundNotApprovedException;
import com.bnd.payment_processing.payment.dto.ApproveRefundRequest;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.ProcessRequest;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.dto.RejectRefundRequest;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.ApprovalStatus;
import com.bnd.payment_processing.payment.model.PaymentMethod;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import com.bnd.payment_processing.payment.repository.PaymentRepository;
import com.bnd.payment_processing.payment.repository.PaymentStatusHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentServiceImpl} (M1's create/get + M3's refund and
 * idempotency logic + M4's search logic). Repositories are mocked - no database needed.
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentStatusHistoryRepository paymentStatusHistoryRepository;

    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PaymentServiceImpl(paymentRepository, paymentStatusHistoryRepository);
    }

    private CreatePaymentRequest newCreateRequest() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setSourceAccount("ACC-1001");
        request.setDestinationAccount("ACC-2002");
        request.setAmount(new BigDecimal("250.00"));
        request.setCurrency("INR");
        request.setIdempotencyKey("idem-key-1");
        return request;
    }

    private Payment completedPayment(BigDecimal amount) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setIdempotencyKey("idem-original");
        payment.setSourceAccount("ACC-1001");
        payment.setDestinationAccount("ACC-2002");
        payment.setAmount(amount);
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.COMPLETED);
        payment.setType(PaymentType.PAYMENT);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());
        return payment;
    }

    private Payment paymentWithStatus(PaymentStatus status) {
        Payment payment = completedPayment(new BigDecimal("100.00"));
        payment.setStatus(status);
        payment.setErrorCode(null);
        return payment;
    }

    /** A freshly created refund sitting at CREATED/PENDING_APPROVAL, as produced by createRefund(). */
    private Payment pendingApprovalRefund() {
        Payment refund = completedPayment(new BigDecimal("100.00"));
        refund.setStatus(PaymentStatus.CREATED);
        refund.setType(PaymentType.REFUND);
        refund.setOriginalPaymentId(UUID.randomUUID());
        refund.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        refund.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        return refund;
    }

    @Test
    void createPayment_happyPath_insertsPaymentAndInitialHistoryRow() {
        CreatePaymentRequest request = newCreateRequest();

        PaymentResponse response = service.createPayment(request);

        verify(paymentRepository).insert(any(Payment.class));
        verify(paymentStatusHistoryRepository).insert(argThat(history ->
                history.getFromStatus() == null
                        && history.getToStatus() == PaymentStatus.CREATED
                        && "SYSTEM".equals(history.getTriggeredBy())));

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.getType()).isEqualTo(PaymentType.PAYMENT);
        assertThat(response.getIdempotencyKey()).isEqualTo("idem-key-1");
    }

    @Test
    void createPayment_duplicateIdempotencyKey_throwsDuplicatePaymentExceptionWithExistingRow() {
        CreatePaymentRequest request = newCreateRequest();
        Payment existing = completedPayment(new BigDecimal("250.00"));
        existing.setIdempotencyKey(request.getIdempotencyKey());

        doThrow(new DuplicateKeyException("duplicate idempotency_key"))
                .when(paymentRepository).insert(any(Payment.class));
        when(paymentRepository.findByIdempotencyKey(request.getIdempotencyKey()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createPayment(request))
                .isInstanceOf(DuplicatePaymentException.class)
                .satisfies(ex -> assertThat(((DuplicatePaymentException) ex).getExistingPayment()).isEqualTo(existing));

        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void createPayment_sameSourceAndDestinationAccount_throwsIllegalArgumentException() {
        CreatePaymentRequest request = newCreateRequest();
        request.setDestinationAccount(request.getSourceAccount());

        assertThatThrownBy(() -> service.createPayment(request))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentRepository, never()).insert(any(Payment.class));
        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void getPayment_notFound_throwsPaymentNotFoundException() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPayment(id))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void getPayment_found_returnsMappedResponse() {
        Payment payment = completedPayment(new BigDecimal("100.00"));
        when(paymentRepository.findById(payment.getId())).thenReturn(Optional.of(payment));

        PaymentResponse response = service.getPayment(payment.getId());

        assertThat(response.getId()).isEqualTo(payment.getId());
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void processTransition_createdToValidated_updatesStatusAndAppendsHistory() {
        Payment current = paymentWithStatus(PaymentStatus.CREATED);
        Payment updated = paymentWithStatus(PaymentStatus.VALIDATED);
        updated.setId(current.getId());

        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current), Optional.of(updated));
        when(paymentRepository.updateStatusIfCurrent(current.getId(), "CREATED", "VALIDATED", null)).thenReturn(1);

        PaymentResponse response = service.processTransition(current.getId(), null);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        verify(paymentRepository).updateStatusIfCurrent(current.getId(), "CREATED", "VALIDATED", null);
        verify(paymentStatusHistoryRepository).insert(argThat(history ->
                history.getFromStatus() == PaymentStatus.CREATED
                        && history.getToStatus() == PaymentStatus.VALIDATED
                        && "SYSTEM".equals(history.getTriggeredBy())
                        && history.getNote() == null));
    }

    @Test
    void processTransition_sentToFailed_withErrorCodeAndNote_persistsBoth() {
        Payment current = paymentWithStatus(PaymentStatus.SENT);
        Payment updated = paymentWithStatus(PaymentStatus.FAILED);
        updated.setId(current.getId());
        updated.setErrorCode("INSUFFICIENT_FUNDS");

        ProcessRequest request = new ProcessRequest();
        request.setTargetStatus("FAILED");
        request.setErrorCode(" INSUFFICIENT_FUNDS ");
        request.setNote("manual test of failure path");

        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current), Optional.of(updated));
        when(paymentRepository.updateStatusIfCurrent(current.getId(), "SENT", "FAILED", "INSUFFICIENT_FUNDS")).thenReturn(1);

        PaymentResponse response = service.processTransition(current.getId(), request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getErrorCode()).isEqualTo("INSUFFICIENT_FUNDS");
        verify(paymentRepository).updateStatusIfCurrent(current.getId(), "SENT", "FAILED", "INSUFFICIENT_FUNDS");
        verify(paymentStatusHistoryRepository).insert(argThat(history ->
                history.getFromStatus() == PaymentStatus.SENT
                        && history.getToStatus() == PaymentStatus.FAILED
                        && "manual test of failure path | errorCode=INSUFFICIENT_FUNDS".equals(history.getNote())));
    }

    @Test
    void processTransition_sentToFailed_withoutNote_setsHistoryNoteToErrorCodeOnly() {
        Payment current = paymentWithStatus(PaymentStatus.SENT);
        Payment updated = paymentWithStatus(PaymentStatus.FAILED);
        updated.setId(current.getId());
        updated.setErrorCode("RISK_REJECTED");

        ProcessRequest request = new ProcessRequest();
        request.setTargetStatus("FAILED");
        request.setErrorCode("RISK_REJECTED");

        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current), Optional.of(updated));
        when(paymentRepository.updateStatusIfCurrent(current.getId(), "SENT", "FAILED", "RISK_REJECTED")).thenReturn(1);

        service.processTransition(current.getId(), request);

        verify(paymentStatusHistoryRepository).insert(argThat(history ->
                history.getToStatus() == PaymentStatus.FAILED
                        && "RISK_REJECTED".equals(history.getNote())));
    }

    @Test
    void processTransition_terminalStatus_throwsInvalidStatusTransitionException() {
        Payment current = paymentWithStatus(PaymentStatus.COMPLETED);
        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.processTransition(current.getId(), new ProcessRequest()))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("terminal status");

        verify(paymentRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void processTransition_targetStatusProvidedOutsideSent_throwsInvalidStatusTransitionException() {
        Payment current = paymentWithStatus(PaymentStatus.CREATED);
        ProcessRequest request = new ProcessRequest();
        request.setTargetStatus("FAILED");
        request.setErrorCode("ANY");

        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.processTransition(current.getId(), request))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("targetStatus can only be specified");

        verify(paymentRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    }

    @Test
    void processTransition_sentWithInvalidTargetStatus_throwsInvalidStatusTransitionException() {
        Payment current = paymentWithStatus(PaymentStatus.SENT);
        ProcessRequest request = new ProcessRequest();
        request.setTargetStatus("VALIDATED");

        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.processTransition(current.getId(), request))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("targetStatus must be 'COMPLETED' or 'FAILED'");

        verify(paymentRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    }

    @Test
    void processTransition_sentToFailed_missingErrorCode_throwsIllegalArgumentException() {
        Payment current = paymentWithStatus(PaymentStatus.SENT);
        ProcessRequest request = new ProcessRequest();
        request.setTargetStatus("FAILED");

        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.processTransition(current.getId(), request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorCode is required");

        verify(paymentRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void processTransition_conditionalUpdateLosesRace_throwsInvalidStatusTransitionException() {
        Payment current = paymentWithStatus(PaymentStatus.VALIDATED);
        when(paymentRepository.findById(current.getId())).thenReturn(Optional.of(current));
        when(paymentRepository.updateStatusIfCurrent(current.getId(), "VALIDATED", "SENT", null)).thenReturn(0);

        assertThatThrownBy(() -> service.processTransition(current.getId(), null))
                .isInstanceOf(InvalidStatusTransitionException.class)
                .hasMessageContaining("changed since read");

        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void processTransition_unknownPayment_throwsPaymentNotFoundException() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.processTransition(id, null))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(paymentRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
    }

    @Test
    void createRefund_happyPath_insertsSwappedAccountsAndCreatedStatus() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        when(paymentRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(paymentRepository.sumRefundedAmount(original.getId())).thenReturn(BigDecimal.ZERO);

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("400.00"));
        request.setReason("customer requested");

        PaymentResponse response = service.createRefund(original.getId(), request);

        assertThat(response.getType()).isEqualTo(PaymentType.REFUND);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.getOriginalPaymentId()).isEqualTo(original.getId());
        assertThat(response.getSourceAccount()).isEqualTo(original.getDestinationAccount());
        assertThat(response.getDestinationAccount()).isEqualTo(original.getSourceAccount());

        verify(paymentStatusHistoryRepository).insert(argThat(history ->
                history.getFromStatus() == null
                        && history.getToStatus() == PaymentStatus.CREATED
                        && "customer requested".equals(history.getNote())));
    }

    @Test
    void createRefund_originalNotCompleted_throwsInvalidRefundStateException() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        original.setStatus(PaymentStatus.SENT);
        when(paymentRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.createRefund(original.getId(), request))
                .isInstanceOf(InvalidRefundStateException.class);
    }

    @Test
    void createRefund_originalIsAlreadyARefund_throwsInvalidRefundStateException() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        original.setType(PaymentType.REFUND);
        when(paymentRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.createRefund(original.getId(), request))
                .isInstanceOf(InvalidRefundStateException.class);
    }

    @Test
    void createRefund_cumulativeAmountExceedsOriginal_throwsInvalidRefundStateException() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        when(paymentRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(paymentRepository.sumRefundedAmount(original.getId())).thenReturn(new BigDecimal("700.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("400.00")); // 700 + 400 > 1000

        assertThatThrownBy(() -> service.createRefund(original.getId(), request))
                .isInstanceOf(InvalidRefundStateException.class);

        verify(paymentRepository, never()).insert(any(Payment.class));
    }

    @Test
    void createRefund_exactFullRemainingAmount_succeeds() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        when(paymentRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(paymentRepository.sumRefundedAmount(original.getId())).thenReturn(new BigDecimal("600.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("400.00")); // 600 + 400 == 1000, exact boundary

        PaymentResponse response = service.createRefund(original.getId(), request);

        assertThat(response.getAmount()).isEqualByComparingTo("400.00");
        verify(paymentRepository).insert(any(Payment.class));
    }

    @Test
    void createRefund_newRefund_startsPendingApprovalAndInheritsOriginalPaymentMethod() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        original.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        when(paymentRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(paymentRepository.sumRefundedAmount(original.getId())).thenReturn(BigDecimal.ZERO);

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("200.00"));

        PaymentResponse response = service.createRefund(original.getId(), request);

        assertThat(response.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING_APPROVAL);
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void createRefund_withIdempotencyKeyAlreadyUsed_throwsDuplicatePaymentExceptionWithExistingRow() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        when(paymentRepository.findByIdForUpdate(original.getId())).thenReturn(Optional.of(original));
        when(paymentRepository.sumRefundedAmount(original.getId())).thenReturn(BigDecimal.ZERO);

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("200.00"));
        request.setIdempotencyKey("refund-idem-key-1");

        Payment existingRefund = pendingApprovalRefund();
        existingRefund.setIdempotencyKey("refund-idem-key-1");

        doThrow(new DuplicateKeyException("duplicate idempotency_key"))
                .when(paymentRepository).insert(any(Payment.class));
        when(paymentRepository.findByIdempotencyKey("refund-idem-key-1"))
                .thenReturn(Optional.of(existingRefund));

        assertThatThrownBy(() -> service.createRefund(original.getId(), request))
                .isInstanceOf(DuplicatePaymentException.class)
                .satisfies(ex -> assertThat(((DuplicatePaymentException) ex).getExistingPayment()).isEqualTo(existingRefund));

        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void processTransition_refundPendingApproval_throwsRefundNotApprovedException() {
        Payment refund = pendingApprovalRefund();
        when(paymentRepository.findById(refund.getId())).thenReturn(Optional.of(refund));

        assertThatThrownBy(() -> service.processTransition(refund.getId(), null))
                .isInstanceOf(RefundNotApprovedException.class);

        verify(paymentRepository, never()).updateStatusIfCurrent(any(), any(), any(), any());
        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void processTransition_refundApproved_createdToValidated_succeedsLikeAnyOtherPayment() {
        Payment refund = pendingApprovalRefund();
        refund.setApprovalStatus(ApprovalStatus.APPROVED);
        Payment updated = pendingApprovalRefund();
        updated.setId(refund.getId());
        updated.setStatus(PaymentStatus.VALIDATED);
        updated.setApprovalStatus(ApprovalStatus.APPROVED);

        when(paymentRepository.findById(refund.getId())).thenReturn(Optional.of(refund), Optional.of(updated));
        when(paymentRepository.updateStatusIfCurrent(refund.getId(), "CREATED", "VALIDATED", null)).thenReturn(1);

        PaymentResponse response = service.processTransition(refund.getId(), null);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.VALIDATED);
        verify(paymentRepository).updateStatusIfCurrent(refund.getId(), "CREATED", "VALIDATED", null);
    }

    @Test
    void approveRefund_happyPath_updatesApprovalFieldsAndReturnsMappedResponse() {
        Payment refund = pendingApprovalRefund();
        Payment approved = pendingApprovalRefund();
        approved.setId(refund.getId());
        approved.setApprovalStatus(ApprovalStatus.APPROVED);
        approved.setApprovedBy("business-user-1");

        when(paymentRepository.findById(refund.getId())).thenReturn(Optional.of(refund), Optional.of(approved));
        when(paymentRepository.approveRefund(eq(refund.getId()), eq("business-user-1"), any(Instant.class))).thenReturn(1);

        ApproveRefundRequest request = new ApproveRefundRequest();
        request.setApprovedBy("business-user-1");

        PaymentResponse response = service.approveRefund(refund.getId(), request);

        assertThat(response.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(response.getApprovedBy()).isEqualTo("business-user-1");
    }

    @Test
    void approveRefund_notPendingApproval_throwsRefundNotApprovedException() {
        Payment refund = pendingApprovalRefund();
        refund.setApprovalStatus(ApprovalStatus.APPROVED);
        when(paymentRepository.findById(refund.getId())).thenReturn(Optional.of(refund));
        when(paymentRepository.approveRefund(eq(refund.getId()), any(), any(Instant.class))).thenReturn(0);

        ApproveRefundRequest request = new ApproveRefundRequest();
        request.setApprovedBy("business-user-1");

        assertThatThrownBy(() -> service.approveRefund(refund.getId(), request))
                .isInstanceOf(RefundNotApprovedException.class);
    }

    @Test
    void approveRefund_unknownPayment_throwsPaymentNotFoundException() {
        UUID id = UUID.randomUUID();
        when(paymentRepository.findById(id)).thenReturn(Optional.empty());

        ApproveRefundRequest request = new ApproveRefundRequest();
        request.setApprovedBy("business-user-1");

        assertThatThrownBy(() -> service.approveRefund(id, request))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(paymentRepository, never()).approveRefund(any(), any(), any());
    }

    @Test
    void rejectRefund_happyPath_appendsCreatedToFailedHistoryRow() {
        Payment refund = pendingApprovalRefund();
        Payment rejected = pendingApprovalRefund();
        rejected.setId(refund.getId());
        rejected.setApprovalStatus(ApprovalStatus.REJECTED);
        rejected.setStatus(PaymentStatus.FAILED);
        rejected.setErrorCode("REFUND_REJECTED");
        rejected.setRejectionReason("duplicate refund request");

        when(paymentRepository.findById(refund.getId())).thenReturn(Optional.of(refund), Optional.of(rejected));
        when(paymentRepository.rejectRefund(refund.getId(), "duplicate refund request")).thenReturn(1);

        RejectRefundRequest request = new RejectRefundRequest();
        request.setRejectedBy("business-user-2");
        request.setReason("duplicate refund request");

        PaymentResponse response = service.rejectRefund(refund.getId(), request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        verify(paymentStatusHistoryRepository).insert(argThat(history ->
                history.getFromStatus() == PaymentStatus.CREATED
                        && history.getToStatus() == PaymentStatus.FAILED
                        && "business-user-2".equals(history.getTriggeredBy())));
    }

    @Test
    void rejectRefund_notPendingApproval_throwsRefundNotApprovedException() {
        Payment refund = pendingApprovalRefund();
        refund.setApprovalStatus(ApprovalStatus.REJECTED);
        when(paymentRepository.findById(refund.getId())).thenReturn(Optional.of(refund));
        when(paymentRepository.rejectRefund(refund.getId(), "already rejected")).thenReturn(0);

        RejectRefundRequest request = new RejectRefundRequest();
        request.setRejectedBy("business-user-2");
        request.setReason("already rejected");

        assertThatThrownBy(() -> service.rejectRefund(refund.getId(), request))
                .isInstanceOf(RefundNotApprovedException.class);

        verify(paymentStatusHistoryRepository, never()).insert(any());
    }

    @Test
    void createPayment_paymentMethodOmitted_defaultsToBankTransfer() {
        CreatePaymentRequest request = newCreateRequest();
        request.setPaymentMethod(null);

        PaymentResponse response = service.createPayment(request);

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void createPayment_explicitPaymentMethod_isPersisted() {
        CreatePaymentRequest request = newCreateRequest();
        request.setPaymentMethod("BANK_TRANSFER");

        PaymentResponse response = service.createPayment(request);

        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.BANK_TRANSFER);
    }

    @Test
    void searchPayments_validStatusAndType_passesUppercasedEnumNamesToRepositoryAndMapsResults() {
        Payment payment = completedPayment(new BigDecimal("100.00"));
        when(paymentRepository.search(any(), eq(0), eq(20))).thenReturn(List.of(payment));
        when(paymentRepository.countSearch(any())).thenReturn(1L);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "completed");
        filters.put("type", "payment");

        Map<String, Object> result = service.searchPayments(filters, 0, 20);

        verify(paymentRepository).search(argThat(f ->
                "COMPLETED".equals(f.get("status")) && "PAYMENT".equals(f.get("type"))), eq(0), eq(20));
        verify(paymentRepository).countSearch(argThat(f ->
                "COMPLETED".equals(f.get("status")) && "PAYMENT".equals(f.get("type"))));

        @SuppressWarnings("unchecked")
        List<PaymentResponse> content = (List<PaymentResponse>) result.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).getId()).isEqualTo(payment.getId());
        assertThat(result.get("page")).isEqualTo(0);
        assertThat(result.get("size")).isEqualTo(20);
        assertThat(result.get("totalElements")).isEqualTo(1L);
    }

    @Test
    void searchPayments_invalidStatus_throwsIllegalArgumentException() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "BOGUS");

        assertThatThrownBy(() -> service.searchPayments(filters, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentRepository, never()).search(any(), anyInt(), anyInt());
    }

    @Test
    void searchPayments_invalidType_throwsIllegalArgumentException() {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("type", "BOGUS");

        assertThatThrownBy(() -> service.searchPayments(filters, 0, 20))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void searchPayments_passThroughFiltersAndPagination_forwardedUnchanged() {
        when(paymentRepository.search(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(paymentRepository.countSearch(any())).thenReturn(0L);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceAccount", "ACC-1001");
        filters.put("destinationAccount", "ACC-2002");
        filters.put("fromDate", LocalDate.of(2026, 8, 1));
        filters.put("toDate", LocalDate.of(2026, 8, 2));

        Map<String, Object> result = service.searchPayments(filters, 2, 10);

        verify(paymentRepository).search(argThat(f ->
                "ACC-1001".equals(f.get("sourceAccount"))
                        && "ACC-2002".equals(f.get("destinationAccount"))
                        && LocalDate.of(2026, 8, 1).equals(f.get("fromDate"))
                        && LocalDate.of(2026, 8, 2).equals(f.get("toDate"))), eq(2), eq(10));
        assertThat(result.get("page")).isEqualTo(2);
        assertThat(result.get("size")).isEqualTo(10);
    }

    @Test
    void searchPayments_noFilters_emptyResult_returnsEmptyContentAndZeroTotal() {
        when(paymentRepository.search(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(paymentRepository.countSearch(any())).thenReturn(0L);

        Map<String, Object> result = service.searchPayments(new LinkedHashMap<>(), 0, 20);

        assertThat((List<?>) result.get("content")).isEmpty();
        assertThat(result.get("totalElements")).isEqualTo(0L);
    }
}