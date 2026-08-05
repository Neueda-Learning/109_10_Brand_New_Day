package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.ApprovalStatus;
import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentMethod;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link PaymentRepository} (no JPA/Hibernate,
 * per spec.md Section 4). Method bodies are stubs until Phase 2.
 */
@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Payment insert(Payment payment) {
        String sql = """
            INSERT INTO payments
            (id, idempotency_key, source_account, destination_account, amount, currency,
             status, error_code, type, original_payment_id, payment_method, approval_status,
             approved_by, approved_at, rejection_reason, created_at, updated_at)
            VALUES (:id, :idempotencyKey, :sourceAccount, :destinationAccount, :amount, :currency,
             :status, :errorCode, :type, :originalPaymentId, :paymentMethod, :approvalStatus,
             :approvedBy, :approvedAt, :rejectionReason, :createdAt, :updatedAt)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", payment.getId().toString())
                .addValue("idempotencyKey", payment.getIdempotencyKey())
                .addValue("sourceAccount", payment.getSourceAccount())
                .addValue("destinationAccount", payment.getDestinationAccount())
                .addValue("amount", payment.getAmount())
                .addValue("currency", payment.getCurrency())
                .addValue("status", payment.getStatus().name())
                .addValue("errorCode", payment.getErrorCode())
                .addValue("type", payment.getType().name())
                .addValue("originalPaymentId",
                        payment.getOriginalPaymentId() == null ? null : payment.getOriginalPaymentId().toString())
                // paymentMethod defaults to BANK_TRANSFER here as a safety net in case a
                // caller forgot to set it - spec.md Section 10.1 (v2.2), NOT NULL column.
                .addValue("paymentMethod",
                        (payment.getPaymentMethod() == null ? PaymentMethod.BANK_TRANSFER : payment.getPaymentMethod()).name())
                .addValue("approvalStatus",
                        payment.getApprovalStatus() == null ? null : payment.getApprovalStatus().name())
                .addValue("approvedBy", payment.getApprovedBy())
                .addValue("approvedAt", payment.getApprovedAt() == null ? null : Timestamp.from(payment.getApprovedAt()))
                .addValue("rejectionReason", payment.getRejectionReason())
                .addValue("createdAt", Timestamp.from(payment.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(payment.getUpdatedAt()));

        jdbcTemplate.update(sql, params);
        return payment;
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        String sql = "SELECT * FROM payments WHERE id = :id";
        List<Payment> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id.toString()),
                this::mapRow
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        String sql = "SELECT * FROM payments WHERE idempotency_key = :key";
        List<Payment> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("key", idempotencyKey),
                this::mapRow
        );
        return results.stream().findFirst();
    }

    @Override
    public Optional<Payment> findByIdForUpdate(UUID id) {
        String sql = "SELECT * FROM payments WHERE id = :id FOR UPDATE";
        List<Payment> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("id", id.toString()),
                this::mapRow
        );
        return results.stream().findFirst();
    }

    @Override
    public int updateStatusIfCurrent(UUID id, String expectedCurrentStatus, String newStatus, String errorCode) {
        String sql = """
                UPDATE payments
                SET status = :newStatus, error_code = :errorCode, updated_at = :updatedAt
                WHERE id = :id AND status = :expectedCurrentStatus
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("newStatus", newStatus)
                .addValue("errorCode", errorCode)
                .addValue("updatedAt", Timestamp.from(Instant.now()))
                .addValue("id", id.toString())
                .addValue("expectedCurrentStatus", expectedCurrentStatus);

        return jdbcTemplate.update(sql, params);
    }

    @Override
    public List<Payment> search(Map<String, Object> filters, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildWhereClause(filters, params);
        String sql = "SELECT * FROM payments" + whereClause
                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", size);
        params.addValue("offset", page * size);
        return jdbcTemplate.query(sql, params, this::mapRow);
    }

    @Override
    public long countSearch(Map<String, Object> filters) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildWhereClause(filters, params);
        String sql = "SELECT COUNT(*) FROM payments" + whereClause;
        Long count = jdbcTemplate.queryForObject(sql, params, Long.class);
        return count == null ? 0L : count;
    }

    /**
     * Builds the optional "WHERE ..." clause for GET /api/payments (spec.md Section
     * 10.3) from whichever of status/type/sourceAccount/destinationAccount/fromDate/
     * toDate are present in {@code filters}, populating {@code params} to match.
     */
    private String buildWhereClause(Map<String, Object> filters, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();

        if (filters.get("status") != null) {
            conditions.add("status = :status");
            params.addValue("status", filters.get("status").toString());
        }
        if (filters.get("type") != null) {
            conditions.add("type = :type");
            params.addValue("type", filters.get("type").toString());
        }
        if (filters.get("sourceAccount") != null) {
            conditions.add("source_account = :sourceAccount");
            params.addValue("sourceAccount", filters.get("sourceAccount").toString());
        }
        if (filters.get("destinationAccount") != null) {
            conditions.add("destination_account = :destinationAccount");
            params.addValue("destinationAccount", filters.get("destinationAccount").toString());
        }
        if (filters.get("fromDate") instanceof LocalDate fromDate) {
            conditions.add("created_at >= :fromInstant");
            params.addValue("fromInstant", Timestamp.from(fromDate.atStartOfDay(ZoneOffset.UTC).toInstant()));
        }
        if (filters.get("toDate") instanceof LocalDate toDate) {
            conditions.add("created_at < :toInstant");
            params.addValue("toInstant", Timestamp.from(toDate.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()));
        }

        return conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
    }

    @Override
    public BigDecimal sumRefundedAmount(UUID originalPaymentId) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0) FROM payments
            WHERE original_payment_id = :originalPaymentId AND type = 'REFUND'
            """;
        BigDecimal sum = jdbcTemplate.queryForObject(
                sql,
                new MapSqlParameterSource("originalPaymentId", originalPaymentId.toString()),
                BigDecimal.class
        );
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public int approveRefund(UUID id, String approvedBy, Instant approvedAt) {
        String sql = """
            UPDATE payments
            SET approval_status = 'APPROVED', approved_by = :approvedBy, approved_at = :approvedAt,
                updated_at = :updatedAt
            WHERE id = :id AND type = 'REFUND' AND approval_status = 'PENDING_APPROVAL'
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.toString())
                .addValue("approvedBy", approvedBy)
                .addValue("approvedAt", Timestamp.from(approvedAt))
                .addValue("updatedAt", Timestamp.from(approvedAt));
        return jdbcTemplate.update(sql, params);
    }

    @Override
    public int rejectRefund(UUID id, String rejectionReason) {
        String sql = """
            UPDATE payments
            SET approval_status = 'REJECTED', rejection_reason = :rejectionReason,
                status = 'FAILED', error_code = 'REFUND_REJECTED', updated_at = :updatedAt
            WHERE id = :id AND type = 'REFUND' AND approval_status = 'PENDING_APPROVAL'
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.toString())
                .addValue("rejectionReason", rejectionReason)
                .addValue("updatedAt", Timestamp.from(Instant.now()));
        return jdbcTemplate.update(sql, params);
    }

    private Payment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Payment p = new Payment();
        p.setId(UUID.fromString(rs.getString("id")));
        p.setIdempotencyKey(rs.getString("idempotency_key"));
        p.setSourceAccount(rs.getString("source_account"));
        p.setDestinationAccount(rs.getString("destination_account"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setCurrency(rs.getString("currency"));
        p.setStatus(PaymentStatus.valueOf(rs.getString("status")));
        p.setErrorCode(rs.getString("error_code"));
        p.setType(PaymentType.valueOf(rs.getString("type")));
        String originalPaymentId = rs.getString("original_payment_id");
        p.setOriginalPaymentId(originalPaymentId == null ? null : UUID.fromString(originalPaymentId));
        String paymentMethod = rs.getString("payment_method");
        p.setPaymentMethod(paymentMethod == null ? null : PaymentMethod.valueOf(paymentMethod));
        String approvalStatus = rs.getString("approval_status");
        p.setApprovalStatus(approvalStatus == null ? null : ApprovalStatus.valueOf(approvalStatus));
        p.setApprovedBy(rs.getString("approved_by"));
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        p.setApprovedAt(approvedAt == null ? null : approvedAt.toInstant());
        p.setRejectionReason(rs.getString("rejection_reason"));
        p.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return p;
    }
}