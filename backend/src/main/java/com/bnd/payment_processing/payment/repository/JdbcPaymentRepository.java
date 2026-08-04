package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.Payment;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link PaymentRepository} (no JPA/Hibernate,
 * per spec.md Section 4). Method bodies are stubs until Phase 2.
 */
@Repository
public class JdbcPaymentRepository implements PaymentRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Payment insert(Payment payment) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M1)");
    }

    @Override
    public Optional<Payment> findById(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M1)");
    }

    @Override
    public Optional<Payment> findByIdempotencyKey(String idempotencyKey) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }

    @Override
    public int updateStatusIfCurrent(UUID id, String expectedCurrentStatus, String newStatus, String errorCode) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }

    @Override
    public List<Payment> search(Map<String, Object> filters, int page, int size) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }

    @Override
    public long countSearch(Map<String, Object> filters) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M4)");
    }

    @Override
    public BigDecimal sumRefundedAmount(UUID originalPaymentId) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M3)");
    }
}
