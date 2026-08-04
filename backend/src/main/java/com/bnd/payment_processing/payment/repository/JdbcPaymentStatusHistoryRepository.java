package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentStatusHistory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link PaymentStatusHistoryRepository}.
 * insert() implemented (M1) so createPayment() can write the initial
 * null -> CREATED row. findByPaymentIdOrderByChangedAtAsc stays a stub for M2.
 */
@Repository
public class JdbcPaymentStatusHistoryRepository implements PaymentStatusHistoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentStatusHistoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PaymentStatusHistory insert(PaymentStatusHistory entry) {
        String sql = """
            INSERT INTO payment_status_history
            (id, payment_id, from_status, to_status, changed_at, triggered_by, note)
            VALUES (:id, :paymentId, :fromStatus, :toStatus, :changedAt, :triggeredBy, :note)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", entry.getId().toString())
                .addValue("paymentId", entry.getPaymentId().toString())
                .addValue("fromStatus", entry.getFromStatus() == null ? null : entry.getFromStatus().name())
                .addValue("toStatus", entry.getToStatus().name())
                .addValue("changedAt", Timestamp.from(entry.getChangedAt()))
                .addValue("triggeredBy", entry.getTriggeredBy())
                .addValue("note", entry.getNote());

        jdbcTemplate.update(sql, params);
        return entry;
    }

    @Override
    public List<PaymentStatusHistory> findByPaymentIdOrderByChangedAtAsc(UUID paymentId) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }
}