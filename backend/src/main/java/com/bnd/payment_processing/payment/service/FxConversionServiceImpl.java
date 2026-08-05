package com.bnd.payment_processing.payment.service;

import com.bnd.payment_processing.payment.model.ExchangeRate;
import com.bnd.payment_processing.payment.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * TODO(Neha): placeholder implementation only - product.md Section 17.1 assigns the
 * real FX lookup/conversion logic to Neha. This stub exists purely so Phase 2's payment
 * creation flow has a stable, injectable seam to call without waiting on her work:
 * - USD -> USD always returns rate 1.00000000 with no exchange_rates row (per
 *   product.md Section 11: "If selected currency is USD, FX rate is 1").
 * - Any other currency does a single latest-rate lookup via
 *   {@link ExchangeRateRepository#findLatestRate(String, String)} and rounds the
 *   converted amount to 2 decimals (HALF_UP) - no rate-lock windows, no expiry, no
 *   caching. Replace this class's body (not its public contract) with the real
 *   implementation + tests.
 */
@Service
public class FxConversionServiceImpl implements FxConversionService {

    private static final String USD = "USD";

    private final ExchangeRateRepository exchangeRateRepository;

    public FxConversionServiceImpl(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public FxConversionResult convertToUsd(BigDecimal amount, String currency) {
        if (USD.equalsIgnoreCase(currency)) {
            return new FxConversionResult(BigDecimal.ONE.setScale(8), null, amount.setScale(2, RoundingMode.HALF_UP));
        }

        ExchangeRate rate = exchangeRateRepository.findLatestRate(currency, USD)
                .orElseThrow(() -> new IllegalStateException(
                        "No exchange rate seeded for " + currency + " -> " + USD));

        BigDecimal usdAmount = amount.multiply(rate.getRate()).setScale(2, RoundingMode.HALF_UP);
        return new FxConversionResult(rate.getRate(), rate.getId(), usdAmount);
    }
}
