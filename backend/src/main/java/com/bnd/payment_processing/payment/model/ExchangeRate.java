package com.bnd.payment_processing.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model mapped to the {@code exchange_rates} table (added 2026-08-06).
 * Fixed/seeded rates only - no live FX calls (see spec.md).
 */
public class ExchangeRate {

    private UUID id;
    private String currency;
    private BigDecimal rateToInr;
    private Instant effectiveAt;
    private String source;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getRateToInr() {
        return rateToInr;
    }

    public void setRateToInr(BigDecimal rateToInr) {
        this.rateToInr = rateToInr;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }

    public void setEffectiveAt(Instant effectiveAt) {
        this.effectiveAt = effectiveAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

