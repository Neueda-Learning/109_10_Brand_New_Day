package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Card;
import com.bnd.payment_processing.payment.model.CardStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcCardRepository implements CardRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCardRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Card> findById(UUID id) {
        String sql = "SELECT * FROM cards WHERE id = :id";
        List<Card> results = jdbcTemplate.query(
                sql, new MapSqlParameterSource("id", id.toString()), this::mapRow);
        return results.stream().findFirst();
    }

    private Card mapRow(ResultSet rs, int rowNum) throws SQLException {
        Card c = new Card();
        c.setId(UUID.fromString(rs.getString("id")));
        c.setCustomerRef(rs.getString("customer_ref"));
        c.setCardBrand(rs.getString("card_brand"));
        c.setMaskedPan(rs.getString("masked_pan"));
        c.setLast4(rs.getString("last4"));
        c.setExpiryMonth(rs.getInt("expiry_month"));
        c.setExpiryYear(rs.getInt("expiry_year"));
        c.setCardholderName(rs.getString("cardholder_name"));
        c.setTokenRef(rs.getString("token_ref"));
        c.setStatus(CardStatus.valueOf(rs.getString("status")));
        c.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        return c;
    }
}

