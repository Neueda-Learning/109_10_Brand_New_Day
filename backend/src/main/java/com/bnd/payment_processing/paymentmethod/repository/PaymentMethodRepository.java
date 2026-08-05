package com.bnd.payment_processing.paymentmethod.repository;

import com.bnd.payment_processing.paymentmethod.model.PaymentMethod;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence contract for the {@code payment_methods} table (spec.md Section 7.3).
 */
public interface PaymentMethodRepository {

    PaymentMethod insert(PaymentMethod paymentMethod);

    Optional<PaymentMethod> findById(UUID id);
}
