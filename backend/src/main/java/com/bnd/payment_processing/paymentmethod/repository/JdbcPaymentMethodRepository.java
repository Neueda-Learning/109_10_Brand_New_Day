package com.bnd.payment_processing.paymentmethod.repository;

import com.bnd.payment_processing.payment.model.PaymentMethodType;
import com.bnd.payment_processing.paymentmethod.model.PaymentMethod;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link PaymentMethodRepository} (no JPA/Hibernate,
 * per spec.md Section 4).
 */
@Repository
public class JdbcPaymentMethodRepository implements PaymentMethodRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentMethodRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PaymentMethod insert(PaymentMethod paymentMethod) {
        String sql = """
            INSERT INTO payment_methods
            (id, customer_id, method_type, display_label, masked_identifier, token_ref, provider, created_at, updated_at)
            VALUES (:id, :customerId, :methodType, :displayLabel, :maskedIdentifier, :tokenRef, :provider, :createdAt, :updatedAt)
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("id", paymentMethod.getId().toString())
                .addValue("customerId", paymentMethod.getCustomerId().toString())
                .addValue("methodType", paymentMethod.getMethodType().name())
                .addValue("displayLabel", paymentMethod.getDisplayLabel())
                .addValue("maskedIdentifier", paymentMethod.getMaskedIdentifier())
                .addValue("tokenRef", paymentMethod.getTokenRef())
                .addValue("provider", paymentMethod.getProvider())
                .addValue("createdAt", Timestamp.from(paymentMethod.getCreatedAt()))
                .addValue("updatedAt", Timestamp.from(paymentMethod.getUpdatedAt()));

        jdbcTemplate.update(sql, params);
        return paymentMethod;
    }

    @Override
    public Optional<PaymentMethod> findById(UUID id) {
        String sql = "SELECT * FROM payment_methods WHERE id = :id";
        List<PaymentMethod> results = jdbcTemplate.query(sql, new MapSqlParameterSource("id", id.toString()), this::mapRow);
        return results.stream().findFirst();
    }

    private PaymentMethod mapRow(ResultSet rs, int rowNum) throws SQLException {
        PaymentMethod pm = new PaymentMethod();
        pm.setId(UUID.fromString(rs.getString("id")));
        pm.setCustomerId(UUID.fromString(rs.getString("customer_id")));
        pm.setMethodType(PaymentMethodType.valueOf(rs.getString("method_type")));
        pm.setDisplayLabel(rs.getString("display_label"));
        pm.setMaskedIdentifier(rs.getString("masked_identifier"));
        pm.setTokenRef(rs.getString("token_ref"));
        pm.setProvider(rs.getString("provider"));
        pm.setCreatedAt(rs.getTimestamp("created_at").toInstant());
        pm.setUpdatedAt(rs.getTimestamp("updated_at").toInstant());
        return pm;
    }
}
