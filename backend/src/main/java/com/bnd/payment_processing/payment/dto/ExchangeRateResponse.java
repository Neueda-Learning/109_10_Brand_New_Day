package com.bnd.payment_processing.payment.dto;
import com.bnd.payment_processing.payment.model.ExchangeRate;
import java.math.BigDecimal;
import java.time.Instant;
/**
 * Response body for {@code GET /api/exchange-rates} (added 2026-08-06). Universal,
 * read-only display of the fixed/seeded FX rates used to settle every payment in INR.
 */
public class ExchangeRateResponse {
    private String currency;
    private BigDecimal rateToInr;
    private Instant effectiveAt;
    public static ExchangeRateResponse fromExchangeRate(ExchangeRate rate) {
        ExchangeRateResponse response = new ExchangeRateResponse();
        response.currency = rate.getCurrency();
        response.rateToInr = rate.getRateToInr();
        response.effectiveAt = rate.getEffectiveAt();
        return response;
    }
    public String getCurrency() {
        return currency;
    }
    public BigDecimal getRateToInr() {
        return rateToInr;
    }
    public Instant getEffectiveAt() {
        return effectiveAt;
    }
}
