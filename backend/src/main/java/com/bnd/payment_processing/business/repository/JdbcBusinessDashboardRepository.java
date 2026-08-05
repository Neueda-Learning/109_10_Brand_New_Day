package com.bnd.payment_processing.business.repository;

import org.springframework.jdbc.core.namedparam.EmptySqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

@Repository
public class JdbcBusinessDashboardRepository implements BusinessDashboardRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcBusinessDashboardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public BigDecimal sumCompletedUsdAmount() {
        String sql = "SELECT COALESCE(SUM(usd_amount), 0) FROM payments WHERE status = 'COMPLETED'";
        return jdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, BigDecimal.class);
    }

    @Override
    public BigDecimal sumGstCollected() {
        String sql = "SELECT COALESCE(SUM(gst_amount), 0) FROM invoices "
                + "WHERE status IN ('PAID', 'REFUND_REQUESTED', 'REFUNDED')";
        return jdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, BigDecimal.class);
    }

    @Override
    public long countInvoices() {
        String sql = "SELECT COUNT(*) FROM invoices";
        Long count = jdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public Map<String, Long> countPaymentsByStatus() {
        String sql = "SELECT status, COUNT(*) AS cnt FROM payments GROUP BY status";
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, EmptySqlParameterSource.INSTANCE, rs -> {
            result.put(rs.getString("status"), rs.getLong("cnt"));
        });
        return result;
    }

    @Override
    public long countPendingSettlements() {
        String sql = "SELECT COUNT(*) FROM payments WHERE settlement_status = 'PENDING'";
        Long count = jdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, Long.class);
        return count == null ? 0 : count;
    }

    @Override
    public long countPendingRefundApprovals() {
        String sql = "SELECT COUNT(*) FROM refunds WHERE approval_status = 'PENDING_APPROVAL'";
        Long count = jdbcTemplate.queryForObject(sql, EmptySqlParameterSource.INSTANCE, Long.class);
        return count == null ? 0 : count;
    }
}
