package com.bnd.payment_processing.refund.repository;

import com.bnd.payment_processing.payment.model.ApprovalStatus;
import com.bnd.payment_processing.refund.model.Refund;
import com.bnd.payment_processing.refund.model.RefundStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link RefundRepository} (no JPA/Hibernate,
 * per spec.md Section 4).
 */
@Repository
public class JdbcRefundRepository implements RefundRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcRefundRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Refund insert(Refund refund) {
        String sql = """
            INSERT INTO refunds
            (id, payment_id, amount, currency, usd_amount, reason, approval_status, status,
             approved_by, approved_at, rejection_reason, created_at, updated_at)
            VALUES (:id, :paymentId, :amount, :currency, :usdAmount, :reason, :approvalStatus, :status,
             :approvedBy, :approvedAt, :rejectionReason, :createdAt, :updatedAt)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", refund.getId().toString())
                .addValue("paymentId", refund.getPaymentId().toString())
                .addValue("amount", refund.getAmount())
                .addValue("currency", refund.getCurrency())
                .addValue("usdAmount", refund.getUsdAmount())
                .addValue("reason", refund.getReason())
                .addValue("approvalStatus", refund.getApprovalStatus().name())
                .addValue("status", refund.getStatus().name())
                .addValue("approvedBy", refund.getApprovedBy())
                .addValue("approvedAt", refund.getApprovedAt() == null ? null : Timestamp.from(refund.getApprovedAt()))
                .addValue("rejectionReason", refund.getRejectionReason())
                .addValue("createdAt", Timestamp.from(refund.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(refund.getUpdatedAt()));

        jdbcTemplate.update(sql, params);
        return refund;
    }

    @Override
    public Optional<Refund> findById(UUID id) {
        String sql = "SELECT * FROM refunds WHERE id = :id";
        List<Refund> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id.toString()), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<Refund> findByPaymentId(UUID paymentId) {
        String sql = "SELECT * FROM refunds WHERE payment_id = :paymentId ORDER BY created_at ASC";
        return jdbcTemplate.query(sql, new MapSqlParameterSource("paymentId", paymentId.toString()), this::mapRow);
    }

    @Override
    public BigDecimal sumActiveAmountByPaymentId(UUID paymentId) {
        String sql = """
            SELECT COALESCE(SUM(amount), 0) FROM refunds
            WHERE payment_id = :paymentId AND status NOT IN ('REJECTED', 'FAILED')
            """;
        BigDecimal sum = jdbcTemplate.queryForObject(
                sql, new MapSqlParameterSource("paymentId", paymentId.toString()), BigDecimal.class);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    @Override
    public int approve(UUID id, String approvedBy, Instant approvedAt) {
        String sql = """
            UPDATE refunds
            SET approval_status = 'APPROVED', status = 'COMPLETED', approved_by = :approvedBy,
                approved_at = :approvedAt, updated_at = :updatedAt
            WHERE id = :id AND approval_status = 'PENDING_APPROVAL'
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.toString())
                .addValue("approvedBy", approvedBy)
                .addValue("approvedAt", Timestamp.from(approvedAt))
                .addValue("updatedAt", Timestamp.from(approvedAt));
        return jdbcTemplate.update(sql, params);
    }

    @Override
    public int reject(UUID id, String rejectionReason) {
        String sql = """
            UPDATE refunds
            SET approval_status = 'REJECTED', status = 'REJECTED', rejection_reason = :rejectionReason,
                updated_at = :updatedAt
            WHERE id = :id AND approval_status = 'PENDING_APPROVAL'
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", id.toString())
                .addValue("rejectionReason", rejectionReason)
                .addValue("updatedAt", Timestamp.from(Instant.now()));
        return jdbcTemplate.update(sql, params);
    }

    private Refund mapRow(ResultSet rs, int rowNum) throws SQLException {
        Refund r = new Refund();
        r.setId(UUID.fromString(rs.getString("id")));
        r.setPaymentId(UUID.fromString(rs.getString("payment_id")));
        r.setAmount(rs.getBigDecimal("amount"));
        r.setCurrency(rs.getString("currency"));
        r.setUsdAmount(rs.getBigDecimal("usd_amount"));
        r.setReason(rs.getString("reason"));
        r.setApprovalStatus(ApprovalStatus.valueOf(rs.getString("approval_status")));
        r.setStatus(RefundStatus.valueOf(rs.getString("status")));
        r.setApprovedBy(rs.getString("approved_by"));
        Timestamp approvedAt = rs.getTimestamp("approved_at");
        r.setApprovedAt(approvedAt == null ? null : approvedAt.toInstant());
        r.setRejectionReason(rs.getString("rejection_reason"));
        r.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        r.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return r;
    }
}
