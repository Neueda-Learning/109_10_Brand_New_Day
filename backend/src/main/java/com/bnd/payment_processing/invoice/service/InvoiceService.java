package com.bnd.payment_processing.invoice.service;

import com.bnd.payment_processing.invoice.model.Invoice;
import com.bnd.payment_processing.invoice.model.InvoiceStatus;

import java.util.UUID;

/**
 * Invoice creation/lookup (product.md Section 10.1 / spec.md Section 9.1).
 */
public interface InvoiceService {

    Invoice createInvoice(UUID customerId, String productCode, String currency);

    Invoice getInvoice(UUID id);

    /**
     * Conditional transition used by {@code PaymentServiceImpl} as the invoice
     * moves through the lifecycle alongside its payment (spec.md Section 9.3).
     */
    void transitionStatusIfCurrent(UUID invoiceId, InvoiceStatus expected, InvoiceStatus target);
}
