package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.common.exception.DuplicatePaymentException;
import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.common.exception.PaymentNotFoundException;
import com.bnd.payment_processing.payment.dto.CreatePaymentRequest;
import com.bnd.payment_processing.payment.dto.PaymentResponse;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.model.Payment;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PaymentServiceImpl} - M1's create/get logic
 * (spec.md Section 9, Section 15). Repositories are mocked - no database needed.
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
    void createRefund_happyPath_insertsSwappedAccountsAndCreatedStatus() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        when(paymentRepository.findById(original.getId())).thenReturn(Optional.of(original));
        when(paymentRepository.sumRefundedAmount(original.getId())).thenReturn(BigDecimal.ZERO);

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("400.00"));
        request.setReason("customer requested");

        PaymentResponse response = service.createRefund(original.getId(), request);

        assertThat(response.getType()).isEqualTo(PaymentType.REFUND);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.CREATED);
        assertThat(response.getOriginalPaymentId()).isEqualTo(original.getId());

        verify(paymentStatusHistoryRepository).insert(argThat(history ->
                history.getFromStatus() == null
                        && history.getToStatus() == PaymentStatus.CREATED
                        && "customer requested".equals(history.getNote())));
    }

    @Test
    void createRefund_originalNotCompleted_throwsInvalidRefundStateException() {
        Payment original = completedPayment(new BigDecimal("1000.00"));
        original.setStatus(PaymentStatus.SENT);
        when(paymentRepository.findById(original.getId())).thenReturn(Optional.of(original));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("100.00"));

        assertThatThrownBy(() -> service.createRefund(original.getId(), request))
                .isInstanceOf(InvalidRefundStateException.class);
    }
}