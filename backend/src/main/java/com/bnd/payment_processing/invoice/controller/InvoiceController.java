package com.bnd.payment_processing.invoice.controller;

import com.bnd.payment_processing.invoice.dto.CreateInvoiceRequest;
import com.bnd.payment_processing.invoice.dto.InvoiceMapper;
import com.bnd.payment_processing.invoice.dto.InvoiceResponse;
import com.bnd.payment_processing.invoice.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * {@code POST /api/invoices} and {@code GET /api/invoices/{id}} (product.md
 * Section 10.1).
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
        var invoice = invoiceService.createInvoice(request.getCustomerId(), request.getProductCode(), request.getCurrency());
        return ResponseEntity.status(HttpStatus.CREATED).body(InvoiceMapper.toResponse(invoice));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID id) {
        return ResponseEntity.ok(InvoiceMapper.toResponse(invoiceService.getInvoice(id)));
    }
}
