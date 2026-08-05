package com.bnd.payment_processing.invoice.repository;

import com.bnd.payment_processing.invoice.model.Invoice;
import com.bnd.payment_processing.invoice.model.InvoiceStatus;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for the {@code invoices} table (spec.md Section 7.2).
 */
public interface InvoiceRepository {

    Invoice insert(Invoice invoice);

    Optional<Invoice> findById(UUID id);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    /**
     * Conditional status update - only applies when the row's current status still
     * matches {@code expectedCurrentStatus}, avoiding lost-update races (same pattern
     * as {@code PaymentRepository.updateStatusIfCurrent}). Returns affected row count.
     */
    int updateStatusIfCurrent(UUID id, InvoiceStatus expectedCurrentStatus, InvoiceStatus newStatus);
}
