package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
             status, error_code, type, original_payment_id, created_at, updated_at)
            VALUES (:id, :idempotencyKey, :sourceAccount, :destinationAccount, :amount, :currency,
             :status, :errorCode, :type, :originalPaymentId, :createdAt, :updatedAt)
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
    public int updateStatusIfCurrent(UUID id, String expectedCurrentStatus, String newStatus, String errorCode) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }

    @Override
    public List<Payment> search(Map<String, Object> filters, int page, int size) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }

    @Override
    public long countSearch(Map<String, Object> filters) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }

    @Override
    public BigDecimal sumRefundedAmount(UUID originalPaymentId) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
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
        p.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return p;
    }
}