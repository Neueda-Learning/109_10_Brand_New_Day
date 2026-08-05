package com.bnd.payment_processing.customer.repository;

import com.bnd.payment_processing.customer.model.Customer;

import java.util.Optional;
import java.util.UUID;

/**
 * Read-mostly persistence contract for the {@code customers} table (spec.md
 * Section 7.1). Customers are seeded (scripts/generate_data_sql.py) - the API
 * surface only ever reads customers (bootstrap, invoice/payment linkage,
 * business dashboard filters), it never creates one.
 */
public interface CustomerRepository {

    Optional<Customer> findById(UUID id);

    Optional<Customer> findByCustomerRef(String customerRef);

    boolean existsById(UUID id);
}
