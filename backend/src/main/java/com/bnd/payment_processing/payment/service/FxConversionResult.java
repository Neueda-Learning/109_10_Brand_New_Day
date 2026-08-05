package com.bnd.payment_processing.payment.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of converting a presentment-currency amount to USD (spec.md Section 11 /
 * product.md Section 11). Carries the rate + source exchange_rates row snapshot that
 * {@code PaymentServiceImpl} stores on the payment (fx_rate / exchange_rate_id /
 * usd_amount).
 */
public record FxConversionResult(BigDecimal rate, UUID exchangeRateId, BigDecimal usdAmount) {
}
