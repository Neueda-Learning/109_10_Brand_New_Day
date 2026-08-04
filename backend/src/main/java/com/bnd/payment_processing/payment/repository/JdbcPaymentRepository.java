package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
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

    private static final RowMapper<Payment> PAYMENT_ROW_MAPPER = (rs, rowNum) -> {
        Payment payment = new Payment();
        payment.setId(UUID.fromString(rs.getString("id")));
        payment.setIdempotencyKey(rs.getString("idempotency_key"));
        payment.setSourceAccount(rs.getString("source_account"));
        payment.setDestinationAccount(rs.getString("destination_account"));
        payment.setAmount(rs.getBigDecimal("amount"));
        payment.setCurrency(rs.getString("currency"));
        payment.setStatus(PaymentStatus.valueOf(rs.getString("status")));
        payment.setErrorCode(rs.getString("error_code"));
        payment.setType(PaymentType.valueOf(rs.getString("type")));
        String originalPaymentId = rs.getString("original_payment_id");
        payment.setOriginalPaymentId(originalPaymentId == null ? null : UUID.fromString(originalPaymentId));
        payment.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        payment.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return payment;
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Payment insert(Payment payment) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M1)");
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M1)");
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }

    @Override
    public int updateStatusIfCurrent(UUID id, String expectedCurrentStatus, String newStatus, String errorCode) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }

    @Override
    public List<Payment> search(Map<String, Object> filters, int page, int size) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildWhereClause(filters, params);
        String sql = "SELECT * FROM payments" + whereClause
                + " ORDER BY created_at DESC, id DESC LIMIT :limit OFFSET :offset";
        params.addValue("limit", size);
        params.addValue("offset", page * size);
        return jdbcTemplate.query(sql, params, PAYMENT_ROW_MAPPER);
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
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }
}
