package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentStatusHistory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

<<<<<<< HEAD
<<<<<<< Updated upstream
=======
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
>>>>>>> Stashed changes
=======
import java.sql.Timestamp;
>>>>>>> 031eb5881d315aca5e4eb62bbd892853b23d27ad
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
        String sql = """
            SELECT id, payment_id, from_status, to_status, changed_at, triggered_by, note
            FROM payment_status_history
            WHERE payment_id = :paymentId
            ORDER BY changed_at ASC
            """;

        List<PaymentStatusHistory> results = jdbcTemplate.query(
                sql,
                new MapSqlParameterSource("paymentId", paymentId.toString()),
                this::mapRow
        );
        return results;
    }

    private PaymentStatusHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
        PaymentStatusHistory h = new PaymentStatusHistory();
        h.setId(UUID.fromString(rs.getString("id")));
        h.setPaymentId(UUID.fromString(rs.getString("payment_id")));

        String fromStatusStr = rs.getString("from_status");
        h.setFromStatus(fromStatusStr == null ? null : PaymentStatus.valueOf(fromStatusStr));

        h.setToStatus(PaymentStatus.valueOf(rs.getString("to_status")));
        h.setChangedAt(rs.getTimestamp("changed_at").toInstant());
        h.setTriggeredBy(rs.getString("triggered_by"));
        h.setNote(rs.getString("note"));

        return h;
    }
}