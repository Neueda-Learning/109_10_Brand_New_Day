package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.ExchangeRate;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-only data access for the {@code exchange_rates} table (spec.md Section 7.4).
 * Plain JDBC access only - the actual FX lookup/conversion decision logic (which rate
 * to apply, rounding, etc.) belongs in
 * {@link com.bnd.payment_processing.payment.service.FxConversionService}, owned by Neha.
 */
public interface ExchangeRateRepository {

    /**
     * Finds the latest seeded rate for the given currency pair (highest
     * {@code effective_at}). Empty if no rate row exists for that pair.
     */
    Optional<ExchangeRate> findLatestRate(String fromCurrency, String toCurrency);

    Optional<ExchangeRate> findById(UUID id);
}
