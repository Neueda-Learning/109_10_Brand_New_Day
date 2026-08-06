package com.bnd.payment_processing.payment.controller;

import com.bnd.payment_processing.payment.dto.ExchangeRateResponse;
import com.bnd.payment_processing.payment.repository.ExchangeRateRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Universal, read-only FX rate display API (added 2026-08-06). No live FX calls -
 * returns the fixed/seeded {@code exchange_rates} rows used to settle every
 * payment in INR (spec.md Section 7).
 */
@RestController
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateController(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @GetMapping
    public List<ExchangeRateResponse> listRates() {
        return exchangeRateRepository.findAll().stream()
                .map(ExchangeRateResponse::fromExchangeRate)
                .toList();
    }
}

