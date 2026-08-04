package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.PaymentStatusHistory;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring JDBC implementation of {@link PaymentStatusHistoryRepository}.
 * Method bodies are stubs until Phase 2 (M2).
 */
@Repository
public class JdbcPaymentStatusHistoryRepository implements PaymentStatusHistoryRepository {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPaymentStatusHistoryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public PaymentStatusHistory insert(PaymentStatusHistory entry) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }

    @Override
    public List<PaymentStatusHistory> findByPaymentIdOrderByChangedAtAsc(UUID paymentId) {
        throw new UnsupportedOperationException("Not implemented yet - Phase 2 (M2)");
    }
}
