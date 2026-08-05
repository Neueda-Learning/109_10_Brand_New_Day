package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.ExchangeRate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link ExchangeRateRepository} (no JPA/Hibernate,
 * per spec.md Section 4/6). Read-only - seed data is the only writer of this table.
 */
@Repository
public class JdbcExchangeRateRepository implements ExchangeRateRepository {

    private static final RowMapper<ExchangeRate> ROW_MAPPER = JdbcExchangeRateRepository::mapRow;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcExchangeRateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<ExchangeRate> findLatestRate(String fromCurrency, String toCurrency) {
        String sql = """
            SELECT * FROM exchange_rates
            WHERE from_currency = :fromCurrency AND to_currency = :toCurrency
            ORDER BY effective_at DESC
            LIMIT 1
            """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("fromCurrency", fromCurrency)
                .addValue("toCurrency", toCurrency);
        List<ExchangeRate> results = jdbcTemplate.query(sql, params, ROW_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public Optional<ExchangeRate> findById(UUID id) {
        String sql = "SELECT * FROM exchange_rates WHERE id = :id";
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("id", id.toString());
        List<ExchangeRate> results = jdbcTemplate.query(sql, params, ROW_MAPPER);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    private static ExchangeRate mapRow(ResultSet rs, int rowNum) throws SQLException {
        ExchangeRate rate = new ExchangeRate();
        rate.setId(UUID.fromString(rs.getString("id")));
        rate.setFromCurrency(rs.getString("from_currency"));
        rate.setToCurrency(rs.getString("to_currency"));
        rate.setRate(rs.getBigDecimal("rate"));
        rate.setEffectiveAt(rs.getTimestamp("effective_at").toInstant());
        rate.setSource(rs.getString("source"));
        rate.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        return rate;
    }
}
