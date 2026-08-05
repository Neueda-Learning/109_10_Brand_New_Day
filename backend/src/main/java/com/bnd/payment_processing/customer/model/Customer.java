package com.bnd.payment_processing.customer.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model mapped to the {@code customers} table (spec.md Section 7.1 /
 * product.md Section 7.1). Plain data holder - no persistence annotations since
 * this project uses raw Spring JDBC (no JPA/Hibernate, see spec.md Section 4).
 */
public class Customer {

    private UUID id;
    private String customerRef;
    private String displayName;
    private String email;
    private String defaultCurrency;
    private Instant createdAt;
    private Instant updatedAt;

    public Customer() {
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCustomerRef() {
        return customerRef;
    }

    public void setCustomerRef(String customerRef) {
        this.customerRef = customerRef;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
