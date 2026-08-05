package com.bnd.payment_processing.invoice.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Hardcoded AI credit pack catalog (product.md Section 16 / spec.md Section 7.2).
 * There is no dedicated "packs" table in the 7-table schema (spec.md Section 7), so
 * pack name/credit units/pricing per currency live here, kept in exact sync with
 * {@code scripts/generate_data_sql.py}'s PACKS dict used to seed demo invoices.
 */
public final class CreditPackCatalog {

    public static final BigDecimal GST_RATE = new BigDecimal("0.18");

    public record Pack(String code, String name, int creditUnits, Map<String, BigDecimal> priceByCurrency) {
    }

    private static final Map<String, Pack> PACKS = new LinkedHashMap<>();

    static {
        PACKS.put("AI_CREDITS_STARTER", new Pack(
                "AI_CREDITS_STARTER",
                "BND AI Starter Credits",
                10000,
                Map.of(
                        "INR", new BigDecimal("999.00"),
                        "USD", new BigDecimal("15.00"),
                        "EUR", new BigDecimal("14.00"))));
        PACKS.put("AI_CREDITS_PRO", new Pack(
                "AI_CREDITS_PRO",
                "BND AI Pro Credits",
                100000,
                Map.of(
                        "INR", new BigDecimal("7999.00"),
                        "USD", new BigDecimal("99.00"),
                        "EUR", new BigDecimal("92.00"))));
        PACKS.put("AI_CREDITS_SCALE", new Pack(
                "AI_CREDITS_SCALE",
                "BND AI Scale Credits",
                500000,
                Map.of(
                        "INR", new BigDecimal("34999.00"),
                        "USD", new BigDecimal("420.00"),
                        "EUR", new BigDecimal("390.00"))));
    }

    private CreditPackCatalog() {
    }

    public static Optional<Pack> findByCode(String productCode) {
        return Optional.ofNullable(PACKS.get(productCode));
    }
}
