package com.bnd.payment_processing.payment.service;

import java.math.BigDecimal;

/**
 * FX lookup/conversion service - OWNED BY NEHA (product.md Section 17.1: exchange_rates
 * table access, FX lookup service, currency selection handling, USD conversion
 * calculation, payment response FX fields).
 *
 * Tharan provides only the data-access seam ({@code exchange_rates} table, schema, seed
 * rows, {@link com.bnd.payment_processing.payment.repository.ExchangeRateRepository}) so
 * this interface has real data to read. {@link FxConversionServiceImpl} below is a
 * PLACEHOLDER implementation only (USD passthrough) - Neha replaces its body with the
 * real lookup/rounding/rate-selection logic and adds her own unit tests
 * (spec.md Section 11 / product.md Section 17.1). Do not extend the real conversion
 * logic here outside of her module.
 */
public interface FxConversionService {

    /**
     * Converts {@code amount} in {@code currency} to USD, returning the rate used, the
     * {@code exchange_rates} row id the rate was snapshotted from (null when
     * currency is already USD), and the rounded (2-decimal) USD amount.
     */
    FxConversionResult convertToUsd(BigDecimal amount, String currency);
}
