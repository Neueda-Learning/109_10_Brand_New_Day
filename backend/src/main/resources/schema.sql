-- Canonical schema (spec.md Section 7 / product.md Section 7). Do not amend without
-- updating spec.md first. 7-table model for the BND AI Billing and Payment Processing
-- Engine (product.md Phase 3 redesign, 2026-08-05) - replaces the earlier 2-table
-- (payments + payment_status_history) single-currency model.
--
-- Drop order is reverse-FK (children before parents); create order is FK-safe
-- (parents before children).

DROP TABLE IF EXISTS refunds;
DROP TABLE IF EXISTS payment_status_history;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS payment_methods;
DROP TABLE IF EXISTS invoices;
DROP TABLE IF EXISTS exchange_rates;
DROP TABLE IF EXISTS customers;

-- 1. customers - customer identity for demo and business filtering (product.md 7.1).
CREATE TABLE customers (
    id                CHAR(36)      NOT NULL PRIMARY KEY,
    customer_ref      VARCHAR(32)   NOT NULL UNIQUE,
    display_name      VARCHAR(100)  NOT NULL,
    email             VARCHAR(150)  NULL,
    default_currency  VARCHAR(3)    NOT NULL,
    created_at        TIMESTAMP     NOT NULL,
    updated_at        TIMESTAMP     NOT NULL
);

-- 2. exchange_rates - hardcoded/seeded rates for deterministic demos (product.md 7.4).
-- Owner: Neha (FX lookup service) - this table + seed rows are provided by Tharan so
-- her service has real data to read; she owns the repository/service logic that reads
-- it (see the FxConversionService stub in the payment.service package).
CREATE TABLE exchange_rates (
    id             CHAR(36)      NOT NULL PRIMARY KEY,
    from_currency  VARCHAR(3)    NOT NULL,
    to_currency    VARCHAR(3)    NOT NULL,
    rate           DECIMAL(18,8) NOT NULL,
    effective_at   TIMESTAMP     NOT NULL,
    source         VARCHAR(40)   NOT NULL,
    created_at     TIMESTAMP     NOT NULL
);

CREATE INDEX idx_exchange_rates_from_to ON exchange_rates (from_currency, to_currency, effective_at);

-- 3. invoices - what the customer owes before payment starts (product.md 7.2).
CREATE TABLE invoices (
    id               CHAR(36)      NOT NULL PRIMARY KEY,
    invoice_number   VARCHAR(32)   NOT NULL UNIQUE,
    customer_id      CHAR(36)      NOT NULL,
    product_name     VARCHAR(100)  NOT NULL,
    product_code     VARCHAR(32)   NOT NULL,
    credit_units     INT           NOT NULL,
    subtotal_amount  DECIMAL(18,2) NOT NULL,
    gst_amount       DECIMAL(18,2) NOT NULL,
    total_amount     DECIMAL(18,2) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    status           VARCHAR(24)   NOT NULL,
    created_at       TIMESTAMP     NOT NULL,
    updated_at       TIMESTAMP     NOT NULL,
    CONSTRAINT fk_invoices_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE INDEX idx_invoices_customer_id ON invoices (customer_id);
CREATE INDEX idx_invoices_status ON invoices (status);
CREATE INDEX idx_invoices_created_at ON invoices (created_at);

-- 4. payment_methods - safe, tokenized, demo payment method references (product.md 7.3).
-- Never stores full card/bank account numbers - masked_identifier + token_ref only.
CREATE TABLE payment_methods (
    id                  CHAR(36)      NOT NULL PRIMARY KEY,
    customer_id         CHAR(36)      NOT NULL,
    method_type         VARCHAR(20)   NOT NULL,
    display_label       VARCHAR(80)   NOT NULL,
    masked_identifier   VARCHAR(64)   NOT NULL,
    token_ref           VARCHAR(100)  NOT NULL,
    provider             VARCHAR(40)   NOT NULL,
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL,
    CONSTRAINT fk_payment_methods_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
);

CREATE INDEX idx_payment_methods_customer_id ON payment_methods (customer_id);

-- 5. payments - core payment engine record (product.md 7.5). Replaces the old
-- type=PAYMENT/REFUND-in-payments pattern: refunds now live in their own `refunds`
-- table (see below), and payments gain invoice/customer/payment-method/FX linkage plus
-- a dedicated settlement_status lifecycle.
CREATE TABLE payments (
    id                  CHAR(36)      NOT NULL PRIMARY KEY,
    invoice_id          CHAR(36)      NOT NULL,
    customer_id         CHAR(36)      NOT NULL,
    payment_method_id   CHAR(36)      NULL,
    idempotency_key     VARCHAR(255)  NOT NULL UNIQUE,
    amount              DECIMAL(18,2) NOT NULL,
    currency            VARCHAR(3)    NOT NULL,
    exchange_rate_id    CHAR(36)      NULL,
    fx_rate             DECIMAL(18,8) NOT NULL,
    usd_amount          DECIMAL(18,2) NOT NULL,
    status              VARCHAR(24)   NOT NULL,
    settlement_status   VARCHAR(24)   NOT NULL,
    error_code          VARCHAR(64)   NULL,
    created_at          TIMESTAMP     NOT NULL,
    updated_at          TIMESTAMP     NOT NULL,
    CONSTRAINT fk_payments_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id),
    CONSTRAINT fk_payments_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT fk_payments_payment_method FOREIGN KEY (payment_method_id) REFERENCES payment_methods (id),
    CONSTRAINT fk_payments_exchange_rate FOREIGN KEY (exchange_rate_id) REFERENCES exchange_rates (id)
);

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_settlement_status ON payments (settlement_status);
CREATE INDEX idx_payments_invoice_id ON payments (invoice_id);
CREATE INDEX idx_payments_customer_id ON payments (customer_id);
CREATE INDEX idx_payments_created_at ON payments (created_at);

-- 6. payment_status_history - append-only lifecycle timeline (product.md 7.6).
-- Unchanged shape from the previous phase; still keyed off payments.id.
CREATE TABLE payment_status_history (
    id           CHAR(36)      NOT NULL PRIMARY KEY,
    payment_id   CHAR(36)      NOT NULL,
    from_status  VARCHAR(24)   NULL,
    to_status    VARCHAR(24)   NOT NULL,
    changed_at   TIMESTAMP     NOT NULL,
    triggered_by VARCHAR(64)   NOT NULL,
    note         VARCHAR(255)  NULL,
    -- Insertion-order tiebreaker: changed_at alone is second-precision and multiple
    -- transitions can land in the same second, which breaks "oldest first" ordering.
    seq          BIGINT        NOT NULL AUTO_INCREMENT UNIQUE,
    CONSTRAINT fk_history_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_history_payment_id ON payment_status_history (payment_id, changed_at, seq);

-- 7. refunds - first-class refund workflow (product.md 7.7). Replaces the old
-- type=REFUND payment-row pattern; refunds are only ever created against a COMPLETED
-- payment, business approval-gated, and cumulative-capped against payments.amount.
CREATE TABLE refunds (
    id                 CHAR(36)      NOT NULL PRIMARY KEY,
    payment_id         CHAR(36)      NOT NULL,
    amount             DECIMAL(18,2) NOT NULL,
    currency           VARCHAR(3)    NOT NULL,
    usd_amount         DECIMAL(18,2) NOT NULL,
    reason             VARCHAR(255)  NULL,
    approval_status    VARCHAR(24)   NOT NULL,
    status             VARCHAR(24)   NOT NULL,
    approved_by        VARCHAR(64)   NULL,
    approved_at        TIMESTAMP     NULL,
    rejection_reason   VARCHAR(255)  NULL,
    created_at         TIMESTAMP     NOT NULL,
    updated_at         TIMESTAMP     NOT NULL,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_refunds_payment_id ON refunds (payment_id);
CREATE INDEX idx_refunds_approval_status ON refunds (approval_status);
CREATE INDEX idx_refunds_status ON refunds (status);
