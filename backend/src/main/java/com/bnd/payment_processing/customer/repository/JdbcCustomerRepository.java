package com.bnd.payment_processing.customer.repository;

import com.bnd.payment_processing.customer.model.Customer;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link CustomerRepository} (no JPA/Hibernate,
 * per spec.md Section 4).
 */
@Repository
public class JdbcCustomerRepository implements CustomerRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCustomerRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Customer> findById(UUID id) {
        String sql = "SELECT * FROM customers WHERE id = :id";
        List<Customer> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id.toString()), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public Optional<Customer> findByCustomerRef(String customerRef) {
        String sql = "SELECT * FROM customers WHERE customer_ref = :customerRef";
        List<Customer> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("customerRef", customerRef), this::mapRow);
        return results.stream().findFirst();
    }

    @Override
    public boolean existsById(UUID id) {
        String sql = "SELECT COUNT(*) FROM customers WHERE id = :id";
        Long count = jdbcTemplate.queryForObject(sql, new MapSqlParameterSource("id", id.toString()), Long.class);
        return count != null && count > 0;
    }

    private Customer mapRow(ResultSet rs, int rowNum) throws SQLException {
        Customer c = new Customer();
        c.setId(UUID.fromString(rs.getString("id")));
        c.setCustomerRef(rs.getString("customer_ref"));
        c.setDisplayName(rs.getString("display_name"));
        c.setEmail(rs.getString("email"));
        c.setDefaultCurrency(rs.getString("default_currency"));
        c.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        c.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return c;
    }
}
