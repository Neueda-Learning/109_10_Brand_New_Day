package com.bnd.payment_processing.bootstrap.service;

import com.bnd.payment_processing.bootstrap.dto.BootstrapResponse;
import com.bnd.payment_processing.bootstrap.dto.BootstrapResponse.BndReceiving;
import com.bnd.payment_processing.bootstrap.dto.BootstrapResponse.CustomerSummary;
import com.bnd.payment_processing.bootstrap.dto.BootstrapResponse.ExchangeRateSummary;
import com.bnd.payment_processing.bootstrap.dto.BootstrapResponse.PackSummary;
import com.bnd.payment_processing.common.exception.CustomerNotFoundException;
import com.bnd.payment_processing.customer.model.Customer;
import com.bnd.payment_processing.customer.repository.CustomerRepository;
import com.bnd.payment_processing.invoice.service.CreditPackCatalog;
import com.bnd.payment_processing.payment.model.PaymentMethodType;
import com.bnd.payment_processing.payment.repository.ExchangeRateRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Assembles the single {@code GET /api/bootstrap} response the checkout page needs
 * before it can render (spec.md Section 7.1). The demo dataset only ever has one
 * checkout customer ("Kishore", seeded by scripts/generate_data_sql.py).
 */
@Service
public class BootstrapServiceImpl implements BootstrapService {

    private static final String CHECKOUT_CUSTOMER_REF = "CUS-KISHORE-001";
    private static final List<String> CURRENCIES = List.of("INR", "USD", "EUR");

    private final CustomerRepository customerRepository;
    private final ExchangeRateRepository exchangeRateRepository;

    public BootstrapServiceImpl(CustomerRepository customerRepository, ExchangeRateRepository exchangeRateRepository) {
        this.customerRepository = customerRepository;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public BootstrapResponse getBootstrap() {
        Customer customer = customerRepository.findByCustomerRef(CHECKOUT_CUSTOMER_REF)
                .orElseThrow(() -> new CustomerNotFoundException("Checkout customer " + CHECKOUT_CUSTOMER_REF + " was not found"));

        CustomerSummary customerSummary = new CustomerSummary(
                customer.getId(), customer.getCustomerRef(), customer.getDisplayName(), customer.getDefaultCurrency());

        BndReceiving bndReceiving = new BndReceiving("BND AI", "BND-USD-OPERATING-001", "USD");

        List<PackSummary> packs = List.of("AI_CREDITS_STARTER", "AI_CREDITS_PRO", "AI_CREDITS_SCALE").stream()
                .map(code -> CreditPackCatalog.findByCode(code)
                        .map(pack -> new PackSummary(pack.code(), pack.name(), pack.creditUnits()))
                        .orElseThrow(() -> new IllegalStateException("Missing catalog entry for " + code)))
                .toList();

        List<ExchangeRateSummary> exchangeRates = new ArrayList<>();
        exchangeRates.add(new ExchangeRateSummary("USD", "USD", BigDecimal.ONE.setScale(8)));
        for (String currency : CURRENCIES) {
            if ("USD".equals(currency)) {
                continue;
            }
            exchangeRateRepository.findLatestRate(currency, "USD")
                    .ifPresent(rate -> exchangeRates.add(new ExchangeRateSummary(currency, "USD", rate.getRate())));
        }

        List<String> paymentMethods = List.of(PaymentMethodType.CARD.name(), PaymentMethodType.BANK_TRANSFER.name());

        return new BootstrapResponse(customerSummary, bndReceiving, packs, CURRENCIES, exchangeRates, paymentMethods);
    }
}
