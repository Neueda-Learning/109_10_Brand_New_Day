package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Payment;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.SettlementStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

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
 * per spec.md Section 4).
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
            (id, invoice_id, customer_id, payment_method_id, idempotency_key, amount, currency,
             exchange_rate_id, fx_rate, usd_amount, status, settlement_status, error_code,
             created_at, updated_at)
            VALUES (:id, :invoiceId, :customerId, :paymentMethodId, :idempotencyKey, :amount, :currency,
             :exchangeRateId, :fxRate, :usdAmount, :status, :settlementStatus, :errorCode,
             :createdAt, :updatedAt)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", payment.getId().toString())
                .addValue("invoiceId", payment.getInvoiceId().toString())
                .addValue("customerId", payment.getCustomerId().toString())
                .addValue("paymentMethodId", payment.getPaymentMethodId() == null ? null : payment.getPaymentMethodId().toString())
                .addValue("idempotencyKey", payment.getIdempotencyKey())
                .addValue("amount", payment.getAmount())
                .addValue("currency", payment.getCurrency())
                .addValue("exchangeRateId", payment.getExchangeRateId() == null ? null : payment.getExchangeRateId().toString())
                .addValue("fxRate", payment.getFxRate())
                .addValue("usdAmount", payment.getUsdAmount())
                .addValue("status", payment.getStatus().name())
                .addValue("settlementStatus", payment.getSettlementStatus().name())
                .addValue("errorCode", payment.getErrorCode())
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
    public int updateSettlementStatusIfCurrent(UUID id, String expectedCurrentSettlementStatus, String newSettlementStatus) {
        String sql = """
                UPDATE payments
                SET settlement_status = :newSettlementStatus, updated_at = :updatedAt
                WHERE id = :id AND settlement_status = :expectedCurrentSettlementStatus
                """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("newSettlementStatus", newSettlementStatus)
                .addValue("updatedAt", Timestamp.from(Instant.now()))
                .addValue("id", id.toString())
                .addValue("expectedCurrentSettlementStatus", expectedCurrentSettlementStatus);

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
     * Builds the optional "WHERE ..." clause for GET /api/payments (product.md
     * Section 10.2) from whichever of status/settlementStatus/currency/customerId/
     * invoiceId/methodType/fromDate/toDate are present in {@code filters}.
     * methodType requires a join to payment_methods since it's not a payments column.
     */
    private String buildWhereClause(Map<String, Object> filters, MapSqlParameterSource params) {
        List<String> conditions = new ArrayList<>();

        if (filters.get("status") != null) {
            conditions.add("status = :status");
            params.addValue("status", filters.get("status").toString());
        }
        if (filters.get("settlementStatus") != null) {
            conditions.add("settlement_status = :settlementStatus");
            params.addValue("settlementStatus", filters.get("settlementStatus").toString());
        }
        if (filters.get("currency") != null) {
            conditions.add("currency = :currency");
            params.addValue("currency", filters.get("currency").toString());
        }
        if (filters.get("customerId") != null) {
            conditions.add("customer_id = :customerId");
            params.addValue("customerId", filters.get("customerId").toString());
        }
        if (filters.get("invoiceId") != null) {
            conditions.add("invoice_id = :invoiceId");
            params.addValue("invoiceId", filters.get("invoiceId").toString());
        }
        if (filters.get("methodType") != null) {
            conditions.add("payment_method_id IN (SELECT id FROM payment_methods WHERE method_type = :methodType)");
            params.addValue("methodType", filters.get("methodType").toString());
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

    private Payment mapRow(ResultSet rs, int rowNum) throws SQLException {
        Payment p = new Payment();
        p.setId(UUID.fromString(rs.getString("id")));
        p.setInvoiceId(UUID.fromString(rs.getString("invoice_id")));
        p.setCustomerId(UUID.fromString(rs.getString("customer_id")));
        String paymentMethodId = rs.getString("payment_method_id");
        p.setPaymentMethodId(paymentMethodId == null ? null : UUID.fromString(paymentMethodId));
        p.setIdempotencyKey(rs.getString("idempotency_key"));
        p.setAmount(rs.getBigDecimal("amount"));
        p.setCurrency(rs.getString("currency"));
        String exchangeRateId = rs.getString("exchange_rate_id");
        p.setExchangeRateId(exchangeRateId == null ? null : UUID.fromString(exchangeRateId));
        p.setFxRate(rs.getBigDecimal("fx_rate"));
        p.setUsdAmount(rs.getBigDecimal("usd_amount"));
        p.setStatus(PaymentStatus.valueOf(rs.getString("status")));
        p.setSettlementStatus(SettlementStatus.valueOf(rs.getString("settlement_status")));
        p.setErrorCode(rs.getString("error_code"));
        p.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        p.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return p;
    }
}
