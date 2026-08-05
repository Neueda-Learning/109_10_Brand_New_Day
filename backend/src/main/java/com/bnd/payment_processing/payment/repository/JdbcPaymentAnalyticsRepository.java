package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.dto.PaymentInsightsResponse;
import com.bnd.payment_processing.payment.model.PaymentStatus;
import com.bnd.payment_processing.payment.model.PaymentType;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Spring JDBC implementation of {@link PaymentAnalyticsRepository} (no JPA/Hibernate,
 * per spec.md Section 4). Runs a handful of GROUP BY aggregate queries rather than
 * loading individual rows, since {@code GET /api/payments/insights} never returns them.
 */
@Repository
public class JdbcPaymentAnalyticsRepository implements PaymentAnalyticsRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentAnalyticsRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PaymentInsightsResponse getInsights(Map<String, Object> filters) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        String whereClause = buildWhereClause(filters, params);

        PaymentInsightsResponse response = new PaymentInsightsResponse();

        Map<String, Object> totals = jdbcTemplate.queryForMap(
                "SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS total FROM payments" + whereClause,
                params);
        response.setTotalCount(((Number) totals.get("cnt")).longValue());
        response.setTotalAmount((BigDecimal) totals.get("total"));

        Map<String, Long> countByStatus = new LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) {
            countByStatus.put(status.name(), 0L);
        }
        jdbcTemplate.query(
                "SELECT status, COUNT(*) AS cnt FROM payments" + whereClause + " GROUP BY status",
                params,
                (RowCallbackHandler) rs -> countByStatus.put(rs.getString("status"), rs.getLong("cnt")));
        response.setCountByStatus(countByStatus);

        Map<String, Long> countByType = new LinkedHashMap<>();
        Map<String, BigDecimal> amountByType = new LinkedHashMap<>();
        for (PaymentType type : PaymentType.values()) {
            countByType.put(type.name(), 0L);
            amountByType.put(type.name(), BigDecimal.ZERO);
        }
        jdbcTemplate.query(
                "SELECT type, COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS total FROM payments"
                        + whereClause + " GROUP BY type",
                params,
                (RowCallbackHandler) rs -> {
                    countByType.put(rs.getString("type"), rs.getLong("cnt"));
                    amountByType.put(rs.getString("type"), rs.getBigDecimal("total"));
                });
        response.setCountByType(countByType);
        response.setAmountByType(amountByType);

        response.setSuccessRate(computeSuccessRate(whereClause, params));

        BigDecimal paymentAmount = amountByType.getOrDefault(PaymentType.PAYMENT.name(), BigDecimal.ZERO);
        BigDecimal refundAmount = amountByType.getOrDefault(PaymentType.REFUND.name(), BigDecimal.ZERO);
        response.setRefundRate(paymentAmount.compareTo(BigDecimal.ZERO) == 0
                ? null
                : refundAmount.divide(paymentAmount, 4, RoundingMode.HALF_UP).doubleValue());

        // approval_status doesn't exist yet - column lands with feature/m3-refund-approval.
        // Stays 0 until that schema migration merges; wire up the real COUNT(*) query then.
        response.setPendingApprovalCount(0L);

        List<PaymentInsightsResponse.DailyVolumeEntry> dailyVolume = new ArrayList<>();
        jdbcTemplate.query(
                "SELECT DATE(created_at) AS day, COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS total FROM payments"
                        + whereClause + " GROUP BY DATE(created_at) ORDER BY day",
                params,
                (RowCallbackHandler) rs -> dailyVolume.add(new PaymentInsightsResponse.DailyVolumeEntry(
                        rs.getDate("day").toLocalDate(), rs.getLong("cnt"), rs.getBigDecimal("total"))));
        response.setDailyVolume(dailyVolume);

        return response;
    }

    /** COMPLETED / (COMPLETED + FAILED) among terminal type=PAYMENT rows (spec.md Section 10.10). */
    private Double computeSuccessRate(String whereClause, MapSqlParameterSource params) {
        MapSqlParameterSource successParams = new MapSqlParameterSource();
        successParams.addValues(params.getValues());
        successParams.addValue("terminalType", PaymentType.PAYMENT.name());
        String successWhere = (whereClause.isEmpty() ? " WHERE " : whereClause + " AND ")
                + "type = :terminalType AND status IN ('COMPLETED','FAILED')";

        Map<String, Long> terminalCounts = new LinkedHashMap<>();
        jdbcTemplate.query(
                "SELECT status, COUNT(*) AS cnt FROM payments" + successWhere + " GROUP BY status",
                successParams,
                (RowCallbackHandler) rs -> terminalCounts.put(rs.getString("status"), rs.getLong("cnt")));

        long completed = terminalCounts.getOrDefault(PaymentStatus.COMPLETED.name(), 0L);
        long failed = terminalCounts.getOrDefault(PaymentStatus.FAILED.name(), 0L);
        return (completed + failed) == 0 ? null : (double) completed / (completed + failed);
    }

    /**
     * Builds the optional "WHERE ..." clause for GET /api/payments/insights (spec.md
     * Section 10.10) from whichever of status/type/fromDate/toDate are present in
     * {@code filters}. Kept separate from JdbcPaymentRepository's version per the
     * spec's recommendation to keep analytics queries independent of the core
     * payment lifecycle repository.
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
}
