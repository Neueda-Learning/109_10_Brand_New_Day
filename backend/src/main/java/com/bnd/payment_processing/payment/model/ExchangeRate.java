package com.bnd.payment_processing.payment.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain model mapped to the {@code exchange_rates} table (spec.md Section 7.4 /
 * product.md Section 7.4). Owner: Neha owns the FX lookup/conversion service that
 * reads this table (see {@link com.bnd.payment_processing.payment.service.FxConversionService}) -
 * this model + its repository are the read-only data-access seam Tharan provides so
 * her service isn't blocked on schema work.
 */
public class ExchangeRate {

    private UUID id;
    private String fromCurrency;
    private String toCurrency;
    private BigDecimal rate;
    private Instant effectiveAt;
    private String source;
    private Instant createdAt;

    public ExchangeRate() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getFromCurrency() {
        return fromCurrency;
    }

    public void setFromCurrency(String fromCurrency) {
        this.fromCurrency = fromCurrency;
    }

    public String getToCurrency() {
        return toCurrency;
    }

    public void setToCurrency(String toCurrency) {
        this.toCurrency = toCurrency;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public void setRate(BigDecimal rate) {
        this.rate = rate;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
