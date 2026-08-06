package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.common.exception.InvalidRefundStateException;
import com.bnd.payment_processing.payment.dto.RefundRequest;
import com.bnd.payment_processing.payment.model.ApprovalStatus;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentMethod;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import com.bnd.payment_processing.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.transaction.TestTransaction;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

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

    // --- Added 2026-08-05 (spec.md Section 8.1 rule 6 / Section 8.3, v2.2): findByIdForUpdate ---

    @Test
    void findByIdForUpdate_existingSeededRow_returnsSameDataAsFindById() {
        Optional<Payment> found = paymentRepository.findByIdForUpdate(SEEDED_COMPLETED_NO_REFUNDS_ID);

        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(SEEDED_COMPLETED_NO_REFUNDS_ID);
        assertThat(found.get().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void findByIdForUpdate_unknownId_returnsEmpty() {
        assertThat(paymentRepository.findByIdForUpdate(UUID.randomUUID())).isEmpty();
    }

    // --- Added 2026-08-05: approveRefund() / rejectRefund() conditional updates ---

    @Test
    void approveRefund_pendingApprovalRow_updatesFieldsAndAffectsOneRow() {
        Payment refund = newRefundRow(SEEDED_COMPLETED_NO_REFUNDS_ID, new BigDecimal("50.00"));
        refund.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        paymentRepository.insert(refund);

        int rowsAffected = paymentRepository.approveRefund(refund.getId(), "business-user-1", Instant.now());

        assertThat(rowsAffected).isEqualTo(1);
        Payment updated = paymentRepository.findById(refund.getId()).orElseThrow();
        assertThat(updated.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(updated.getApprovedBy()).isEqualTo("business-user-1");
        assertThat(updated.getApprovedAt()).isNotNull();
    }

    @Test
    void approveRefund_alreadyApprovedRow_affectsZeroRows() {
        Payment refund = newRefundRow(SEEDED_COMPLETED_NO_REFUNDS_ID, new BigDecimal("50.00"));
        refund.setApprovalStatus(ApprovalStatus.APPROVED);
        paymentRepository.insert(refund);

        int rowsAffected = paymentRepository.approveRefund(refund.getId(), "business-user-1", Instant.now());

        assertThat(rowsAffected).isEqualTo(0);
    }

    @Test
    void approveRefund_paymentTypeRow_affectsZeroRows() {
        int rowsAffected = paymentRepository.approveRefund(SEEDED_COMPLETED_NO_REFUNDS_ID, "business-user-1", Instant.now());

        assertThat(rowsAffected).isEqualTo(0);
    }

    @Test
    void rejectRefund_pendingApprovalRow_movesStatusToFailedAndAffectsOneRow() {
        Payment refund = newRefundRow(SEEDED_COMPLETED_NO_REFUNDS_ID, new BigDecimal("50.00"));
        refund.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        paymentRepository.insert(refund);

        int rowsAffected = paymentRepository.rejectRefund(refund.getId(), "duplicate request");

        assertThat(rowsAffected).isEqualTo(1);
        Payment updated = paymentRepository.findById(refund.getId()).orElseThrow();
        assertThat(updated.getApprovalStatus()).isEqualTo(ApprovalStatus.REJECTED);
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(updated.getErrorCode()).isEqualTo("REFUND_REJECTED");
        assertThat(updated.getRejectionReason()).isEqualTo("duplicate request");
    }

    @Test
    void rejectRefund_alreadyRejectedRow_affectsZeroRows() {
        Payment refund = newRefundRow(SEEDED_COMPLETED_NO_REFUNDS_ID, new BigDecimal("50.00"));
        refund.setApprovalStatus(ApprovalStatus.REJECTED);
        paymentRepository.insert(refund);

        int rowsAffected = paymentRepository.rejectRefund(refund.getId(), "already rejected");

        assertThat(rowsAffected).isEqualTo(0);
    }

    // --- Added 2026-08-05 (spec.md Section 8.3, v2.2): real DB concurrency check for the
    // FOR UPDATE lock taken by findByIdForUpdate() inside PaymentServiceImpl.createRefund(). ---

    @Test
    void createRefund_concurrentRequestsExceedingBalance_onlyOneSucceeds() throws Exception {
        // Bank-grade account validation (added 2026-08-06): PaymentServiceImpl.createRefund()
        // now requires both accounts to exist/be ACTIVE - register these ad-hoc test
        // accounts for real (same commit/cleanup pattern as the payment row below).
        MapSqlParameterSource srcAccountParams = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("accountNumber", "ACC-CONCUR-SRC")
                .addValue("customerRef", "CUS-CONCUR-TEST")
                .addValue("displayName", "Concurrency Test Source")
                .addValue("now", java.sql.Timestamp.from(Instant.now()));
        MapSqlParameterSource dstAccountParams = new MapSqlParameterSource()
                .addValue("id", UUID.randomUUID().toString())
                .addValue("accountNumber", "ACC-CONCUR-DST")
                .addValue("customerRef", "CUS-CONCUR-TEST")
                .addValue("displayName", "Concurrency Test Destination")
                .addValue("now", java.sql.Timestamp.from(Instant.now()));
        // Balance seeded well above either refund attempt (700.00) so the fail-fast
        // solvency guard in PaymentServiceImpl.createRefund() (added 2026-08-06) never
        // short-circuits this test - the thing under test here is the FOR UPDATE lock
        // serializing the cumulative-refund-vs-original-amount check, not the account
        // balance guard.
        String insertAccountSql = """
                INSERT INTO accounts (id, account_number, customer_ref, display_name, account_type, status, default_currency, balance, created_at, updated_at)
                VALUES (:id, :accountNumber, :customerRef, :displayName, 'CUSTOMER', 'ACTIVE', 'INR', 100000.00, :now, :now)
                """;
        jdbcTemplate.update(insertAccountSql, srcAccountParams);
        jdbcTemplate.update(insertAccountSql, dstAccountParams);

        Payment original = new Payment();
        original.setId(UUID.randomUUID());
        original.setIdempotencyKey("concurrency-test-" + original.getId());
        original.setSourceAccount("ACC-CONCUR-SRC");
        original.setDestinationAccount("ACC-CONCUR-DST");
        original.setAmount(new BigDecimal("1000.00"));
        original.setCurrency("INR");
        original.setStatus(PaymentStatus.COMPLETED);
        original.setType(PaymentType.PAYMENT);
        original.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        original.setCreatedAt(Instant.now());
        original.setUpdatedAt(Instant.now());
        paymentRepository.insert(original);
        // Commit this row for real so the two worker threads below (each running
        // PaymentServiceImpl.createRefund() in its own @Transactional/connection) can see
        // it - this test method's own transaction is normally rolled back automatically
        // by the class-level @Transactional.
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();

        try {
            RefundRequest request1 = new RefundRequest();
            request1.setAmount(new BigDecimal("700.00"));
            RefundRequest request2 = new RefundRequest();
            request2.setAmount(new BigDecimal("700.00"));

            ExecutorService pool = Executors.newFixedThreadPool(2);
            List<Callable<Boolean>> tasks = List.of(
                    () -> attemptRefund(original.getId(), request1),
                    () -> attemptRefund(original.getId(), request2)
            );
            List<Future<Boolean>> futures = pool.invokeAll(tasks);
            pool.shutdown();

            long successCount = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successCount++;
                }
            }

            // Only one of the two 700.00 refunds can fit within the 1000.00 balance - the
            // FOR UPDATE lock in findByIdForUpdate() must serialize the two attempts so the
            // cumulative check in the second one sees the first one's committed refund.
            assertThat(successCount).isEqualTo(1);
            BigDecimal totalRefunded = paymentRepository.sumRefundedAmount(original.getId());
            assertThat(totalRefunded).isLessThanOrEqualTo(new BigDecimal("1000.00"));
        } finally {
            // The successful worker thread committed a real refund row (and the original
            // payment above was force-committed too) - delete both for real so the shared
            // dataset isn't polluted, regardless of test outcome.
            MapSqlParameterSource params = new MapSqlParameterSource("originalId", original.getId().toString());
            jdbcTemplate.update("DELETE FROM payment_status_history WHERE payment_id IN "
                    + "(SELECT id FROM payments WHERE original_payment_id = :originalId OR id = :originalId)", params);
            // Delete child refund rows before the parent original row - a single combined
            // DELETE (children OR parent) can process the parent first within the same
            // statement and violate the fk_payments_original_payment constraint.
            jdbcTemplate.update("DELETE FROM payments WHERE original_payment_id = :originalId", params);
            jdbcTemplate.update("DELETE FROM payments WHERE id = :originalId", params);
            jdbcTemplate.update("DELETE FROM accounts WHERE account_number IN ('ACC-CONCUR-SRC', 'ACC-CONCUR-DST')",
                    new MapSqlParameterSource());
            TestTransaction.flagForCommit();
            TestTransaction.end();
        }
    }

    private boolean attemptRefund(UUID originalId, RefundRequest request) {
        try {
            paymentService.createRefund(originalId, request);
            return true;
        } catch (InvalidRefundStateException e) {
            return false;
        }
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
    void search_filterByPaymentMethodAndApprovalStatus_returnsOnlyMatchingRows() {
        Payment matching = newSearchTestRow(PaymentStatus.CREATED, PaymentType.REFUND, Instant.now());
        matching.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        matching.setApprovalStatus(ApprovalStatus.PENDING_APPROVAL);
        paymentRepository.insert(matching);

        Payment nonMatching = newSearchTestRow(PaymentStatus.CREATED, PaymentType.REFUND, Instant.now());
        nonMatching.setPaymentMethod(PaymentMethod.BANK_TRANSFER);
        nonMatching.setApprovalStatus(ApprovalStatus.APPROVED);
        paymentRepository.insert(nonMatching);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("sourceAccount", SEARCH_TEST_SOURCE);
        filters.put("paymentMethod", "BANK_TRANSFER");
        filters.put("approvalStatus", "PENDING_APPROVAL");

        List<Payment> results = paymentRepository.search(filters, 0, 20);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(matching.getId());
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
