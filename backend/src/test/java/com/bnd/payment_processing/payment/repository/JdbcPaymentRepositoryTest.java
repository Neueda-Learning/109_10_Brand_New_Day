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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    // Distinct account names not used anywhere in the seeded data.sql, so these tests
    // only ever see the rows they insert themselves.
    private static final String SEARCH_TEST_SOURCE = "ACC-SEARCHTEST-SRC";
    private static final String SEARCH_TEST_DEST = "ACC-SEARCHTEST-DST";

    private Payment newSearchTestRow(PaymentStatus status, PaymentType type, Instant createdAt) {
        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setIdempotencyKey("search-test-idem-" + payment.getId());
        payment.setSourceAccount(SEARCH_TEST_SOURCE);
        payment.setDestinationAccount(SEARCH_TEST_DEST);
        payment.setAmount(new BigDecimal("10.00"));
        payment.setCurrency("INR");
        payment.setStatus(status);
        payment.setType(type);
        payment.setCreatedAt(createdAt);
        payment.setUpdatedAt(createdAt);
        return payment;
    }

    @Test
    void search_filterByStatusAndType_returnsOnlyMatchingRows() {
        paymentRepository.insert(newSearchTestRow(PaymentStatus.COMPLETED, PaymentType.PAYMENT, Instant.now()));
        paymentRepository.insert(newSearchTestRow(PaymentStatus.FAILED, PaymentType.PAYMENT, Instant.now()));
        paymentRepository.insert(newSearchTestRow(PaymentStatus.COMPLETED, PaymentType.REFUND, Instant.now()));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "COMPLETED");
        filters.put("type", "PAYMENT");
        filters.put("sourceAccount", SEARCH_TEST_SOURCE);

        List<Payment> results = paymentRepository.search(filters, 0, 20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(results.get(0).getType()).isEqualTo(PaymentType.PAYMENT);
    }

    @Test
    void countSearch_matchesSizeOfSearchResultsForSameFilters() {
        paymentRepository.insert(newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, Instant.now()));
        paymentRepository.insert(newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, Instant.now()));
        paymentRepository.insert(newSearchTestRow(PaymentStatus.SENT, PaymentType.PAYMENT, Instant.now()));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("status", "CREATED");
        filters.put("sourceAccount", SEARCH_TEST_SOURCE);

        long count = paymentRepository.countSearch(filters);

        assertThat(count).isEqualTo(2L);
    }

    @Test
    void search_dateRangeFilter_excludesRowsOutsideRange() {
        Instant inRange = LocalDate.of(2026, 8, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant beforeRange = LocalDate.of(2026, 7, 1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant afterRange = LocalDate.of(2026, 9, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

        paymentRepository.insert(newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, inRange));
        paymentRepository.insert(newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, beforeRange));
        paymentRepository.insert(newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, afterRange));

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceAccount", SEARCH_TEST_SOURCE);
        filters.put("fromDate", LocalDate.of(2026, 7, 15));
        filters.put("toDate", LocalDate.of(2026, 8, 15));

        List<Payment> results = paymentRepository.search(filters, 0, 20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCreatedAt()).isEqualTo(inRange);
    }

    @Test
    void search_pagination_returnsCorrectPageAndOrdersNewestFirst() {
        Instant now = Instant.now();
        Payment oldest = newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, now.minusSeconds(20));
        Payment middle = newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, now.minusSeconds(10));
        Payment newest = newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, now);
        paymentRepository.insert(oldest);
        paymentRepository.insert(middle);
        paymentRepository.insert(newest);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceAccount", SEARCH_TEST_SOURCE);

        List<Payment> firstPage = paymentRepository.search(filters, 0, 2);
        List<Payment> secondPage = paymentRepository.search(filters, 1, 2);

        assertThat(firstPage).hasSize(2);
        assertThat(firstPage.get(0).getId()).isEqualTo(newest.getId());
        assertThat(firstPage.get(1).getId()).isEqualTo(middle.getId());
        assertThat(secondPage).hasSize(1);
        assertThat(secondPage.get(0).getId()).isEqualTo(oldest.getId());
    }

    @Test
    void search_noFilters_doesNotThrowAndIncludesInsertedRow() {
        Payment payment = newSearchTestRow(PaymentStatus.CREATED, PaymentType.PAYMENT, Instant.now());
        paymentRepository.insert(payment);

        List<Payment> results = paymentRepository.search(new LinkedHashMap<>(), 0, 1000);

        assertThat(results).extracting(Payment::getId).contains(payment.getId());
    }
}
