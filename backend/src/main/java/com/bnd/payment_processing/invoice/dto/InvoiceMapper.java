package com.bnd.payment_processing.invoice.dto;

import com.bnd.payment_processing.invoice.model.Invoice;

/**
 * Static mapper: {@link Invoice} domain model -> {@link InvoiceResponse}
 * (same pattern as {@code payment.dto.PaymentMapper}).
 */
public final class InvoiceMapper {

    private InvoiceMapper() {
    }

    public static InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomerId(),
                invoice.getProductName(),
                invoice.getProductCode(),
                invoice.getCreditUnits(),
                invoice.getSubtotalAmount(),
                invoice.getGstAmount(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getStatus().name(),
                invoice.getCreatedAt(),
                invoice.getUpdatedAt());
    }
}
