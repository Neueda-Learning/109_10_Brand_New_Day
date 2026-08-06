package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Account;
import com.bnd.payment_processing.payment.model.AccountStatus;
import com.bnd.payment_processing.payment.model.AccountType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcAccountRepository implements AccountRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcAccountRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Account> findByAccountNumber(String accountNumber) {
        String sql = "SELECT * FROM accounts WHERE account_number = :accountNumber";
        List<Account> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("accountNumber", accountNumber), this::mapRow);
        return results.stream().findFirst();
    }

    private Account mapRow(ResultSet rs, int rowNum) throws SQLException {
        Account a = new Account();
        a.setId(UUID.fromString(rs.getString("id")));
        a.setAccountNumber(rs.getString("account_number"));
        a.setCustomerRef(rs.getString("customer_ref"));
        a.setDisplayName(rs.getString("display_name"));
        a.setAccountType(AccountType.valueOf(rs.getString("account_type")));
        a.setStatus(AccountStatus.valueOf(rs.getString("status")));
        a.setDefaultCurrency(rs.getString("default_currency"));
        a.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        a.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return a;
    }
}

