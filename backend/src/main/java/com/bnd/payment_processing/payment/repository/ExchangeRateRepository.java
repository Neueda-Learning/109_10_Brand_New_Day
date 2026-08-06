package com.bnd.payment_processing.payment.repository;

import com.bnd.payment_processing.payment.model.ExchangeRate;

import java.util.Optional;

/** Persistence contract for the {@code exchange_rates} table (added 2026-08-06). */
public interface ExchangeRateRepository {

    Optional<ExchangeRate> findByCurrency(String currency);
}

