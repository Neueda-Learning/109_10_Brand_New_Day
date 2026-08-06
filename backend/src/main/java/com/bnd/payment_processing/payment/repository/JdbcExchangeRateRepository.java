package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.ExchangeRate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcExchangeRateRepository implements ExchangeRateRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcExchangeRateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ExchangeRate> findByCurrency(String currency) {
        String sql = "SELECT * FROM exchange_rates WHERE currency = :currency";
        List<ExchangeRate> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("currency", currency), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public List<ExchangeRate> findAll() {
        return jdbcTemplate.query("SELECT * FROM exchange_rates ORDER BY currency", this::mapRow);
    }

    private ExchangeRate mapRow(ResultSet rs, int rowNum) throws SQLException {
        ExchangeRate e = new ExchangeRate();
        e.setId(UUID.fromString(rs.getString("id")));
        e.setCurrency(rs.getString("currency"));
        e.setRateToInr(rs.getBigDecimal("rate_to_inr"));
        e.setEffectiveAt(rs.getTimestamp("effective_at").toInstant());
        e.setSource(rs.getString("source"));
        return e;
    }
}

