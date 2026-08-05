package com.bnd.payment_processing.invoice.service;

import com.bnd.payment_processing.common.exception.CustomerNotFoundException;
import com.bnd.payment_processing.common.exception.InvoiceNotFoundException;
import com.bnd.payment_processing.customer.repository.CustomerRepository;
import com.bnd.payment_processing.invoice.model.Invoice;
import com.bnd.payment_processing.invoice.model.InvoiceStatus;
import com.bnd.payment_processing.invoice.repository.InvoiceRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class InvoiceServiceImpl implements InvoiceService {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private final InvoiceRepository invoiceRepository;
    private final CustomerRepository customerRepository;

    public InvoiceServiceImpl(InvoiceRepository invoiceRepository, CustomerRepository customerRepository) {
        this.invoiceRepository = invoiceRepository;
        this.customerRepository = customerRepository;
    }

    @Override
    public Invoice createInvoice(UUID customerId, String productCode, String currency) {
        if (!customerRepository.existsById(customerId)) {
            throw new CustomerNotFoundException(customerId);
        }

        CreditPackCatalog.Pack pack = CreditPackCatalog.findByCode(productCode)
                .orElseThrow(() -> new IllegalArgumentException("Unknown productCode: " + productCode));

        String normalizedCurrency = currency == null ? null : currency.toUpperCase();
        BigDecimal subtotal = pack.priceByCurrency().get(normalizedCurrency);
        if (subtotal == null) {
            throw new IllegalArgumentException("Unsupported currency for " + productCode + ": " + currency);
        }

        BigDecimal gst = subtotal.multiply(CreditPackCatalog.GST_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal total = subtotal.add(gst);

        Instant now = Instant.now();
        Invoice invoice = new Invoice();
        invoice.setId(UUID.randomUUID());
        invoice.setInvoiceNumber(nextInvoiceNumber());
        invoice.setCustomerId(customerId);
        invoice.setProductName(pack.name());
        invoice.setProductCode(pack.code());
        invoice.setCreditUnits(pack.creditUnits());
        invoice.setSubtotalAmount(subtotal);
        invoice.setGstAmount(gst);
        invoice.setTotalAmount(total);
        invoice.setCurrency(normalizedCurrency);
        invoice.setStatus(InvoiceStatus.ISSUED);
        invoice.setCreatedAt(now);
        invoice.setUpdatedAt(now);

        return invoiceRepository.insert(invoice);
    }

    @Override
    public Invoice getInvoice(UUID id) {
        return invoiceRepository.findById(id).orElseThrow(() -> new InvoiceNotFoundException(id));
    }

    @Override
    public void transitionStatusIfCurrent(UUID invoiceId, InvoiceStatus expected, InvoiceStatus target) {
        invoiceRepository.updateStatusIfCurrent(invoiceId, expected, target);
    }

    private String nextInvoiceNumber() {
        // Runtime-created invoices use a distinct prefix from the seeded
        // INV-BND-###### numbers (scripts/generate_data_sql.py) to avoid collisions.
        return "INV-RT-" + Instant.now().toEpochMilli() + "-" + SEQUENCE.incrementAndGet();
    }
}
