package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository-level tests for {@link JdbcPaymentRepository} against the real,
 * locally-running MySQL instance (spec.md Section 15 - requires `docker compose up -d`).
 * Each test runs inside a transaction that is rolled back afterward, so the shared
 * seeded dataset (data.sql) is never mutated.
 */
@SpringBootTest
@Transactional
class JdbcPaymentRepositoryTest {

    @Autowired
    private PaymentRepository paymentRepository;

    // Seeded COMPLETED PAYMENT with no refunds against it - data.sql line 8.
    private static final UUID SEEDED_COMPLETED_NO_REFUNDS_ID =
            UUID.fromString("0f48e799-7b79-573f-9693-9a51444d9e88");
    private static final String SEEDED_IDEMPOTENCY_KEY = "idem-payment-00001";

    @Test
    void findByIdempotencyKey_existingSeededRow_returnsPresent() {
        Optional<Payment> found = paymentRepository.findByIdempotencyKey(SEEDED_IDEMPOTENCY_KEY);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(SEEDED_COMPLETED_NO_REFUNDS_ID);
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void findByIdempotencyKey_unknownKey_returnsEmpty() {
        assertThat(paymentRepository.findByIdempotencyKey("does-not-exist-key")).isEmpty();
    }

    @Test
    void insertAndFindById_roundTrip() {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setIdempotencyKey("test-idem-" + payment.getId());
        payment.setSourceAccount("ACC-TEST-1");
        payment.setDestinationAccount("ACC-TEST-2");
        payment.setAmount(new BigDecimal("42.50"));
        payment.setCurrency("INR");
        payment.setStatus(PaymentStatus.CREATED);
        payment.setType(PaymentType.PAYMENT);
        payment.setCreatedAt(Instant.now());
        payment.setUpdatedAt(Instant.now());

        paymentRepository.insert(payment);

        Optional<Payment> found = paymentRepository.findById(payment.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getAmount()).isEqualByComparingTo("42.50");
        assertThat(found.get().getSourceAccount()).isEqualTo("ACC-TEST-1");
    }

    @Test
    void sumRefundedAmount_paymentWithNoRefunds_returnsZero() {
        BigDecimal sum = paymentRepository.sumRefundedAmount(SEEDED_COMPLETED_NO_REFUNDS_ID);

        assertThat(sum).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void sumRefundedAmount_afterInsertingRefundRows_reflectsTheirTotal() {
        Payment refund1 = newRefundRow(SEEDED_COMPLETED_NO_REFUNDS_ID, new BigDecimal("100.00"));
        Payment refund2 = newRefundRow(SEEDED_COMPLETED_NO_REFUNDS_ID, new BigDecimal("50.00"));
        paymentRepository.insert(refund1);
        paymentRepository.insert(refund2);

        BigDecimal sum = paymentRepository.sumRefundedAmount(SEEDED_COMPLETED_NO_REFUNDS_ID);

        assertThat(sum).isEqualByComparingTo("150.00");
    }

    private Payment newRefundRow(UUID originalPaymentId, BigDecimal amount) {
        Payment refund = new Payment();
        refund.setId(UUID.randomUUID());
        refund.setIdempotencyKey("refund-" + refund.getId());
        refund.setSourceAccount("ACC-1033");
        refund.setDestinationAccount("ACC-1000");
        refund.setAmount(amount);
        refund.setCurrency("INR");
        refund.setStatus(PaymentStatus.CREATED);
        refund.setType(PaymentType.REFUND);
        refund.setOriginalPaymentId(originalPaymentId);
        refund.setCreatedAt(Instant.now());
        refund.setUpdatedAt(Instant.now());
        return refund;
    }
}
