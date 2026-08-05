package com.bnd.payment_processing.paymentmethod.service;

import com.bnd.payment_processing.payment.model.PaymentMethodType;
import com.bnd.payment_processing.paymentmethod.model.PaymentMethod;

import java.util.UUID;

/**
 * Creates {@code payment_methods} rows. Each payment creation request supplies
 * its own masked/token details inline (product.md Section 10.1) - this phase
 * does not attempt to dedupe/reuse an existing method for a customer, it simply
 * records the method used for that payment (spec.md Section 8).
 */
public interface PaymentMethodService {

    PaymentMethod createForPayment(
            UUID customerId,
            PaymentMethodType methodType,
            String maskedIdentifier,
            String tokenRef,
            String provider);
}
