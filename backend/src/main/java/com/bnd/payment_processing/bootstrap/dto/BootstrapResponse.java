package com.bnd.payment_processing.bootstrap.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Response body for {@code GET /api/bootstrap} (spec.md Section 7.1).
 */
public record BootstrapResponse(
        CustomerSummary customer,
        BndReceiving bndReceiving,
        List<PackSummary> packs,
        List<String> currencies,
        List<ExchangeRateSummary> exchangeRates,
        List<String> paymentMethods) {

    public record CustomerSummary(UUID id, String customerRef, String displayName, String defaultCurrency) {
    }

    public record BndReceiving(String merchant, String receivingAccount, String settlementCurrency) {
    }

    public record PackSummary(String productCode, String productName, int creditUnits) {
    }

    public record ExchangeRateSummary(String fromCurrency, String toCurrency, BigDecimal rate) {
    }
}
