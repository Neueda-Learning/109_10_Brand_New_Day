-- Canonical schema (spec.md Section 7). Do not amend without updating the spec first.

DROP TABLE IF EXISTS payment_status_history;
DROP TABLE IF EXISTS payments;
DROP TABLE IF EXISTS cards;
DROP TABLE IF EXISTS accounts;
DROP TABLE IF EXISTS exchange_rates;

-- Added 2026-08-06 (bank-grade validation hardening): reference registry of known
-- bank accounts. Simulates a core-banking "does this account exist / is it active"
-- check. Not FK-linked from `payments` (payments.source/destination_account stay
-- free VARCHAR, validated in application code) so an account can be
-- blocked/closed later without breaking historical payment rows.
CREATE TABLE accounts (
    id               CHAR(36)     NOT NULL PRIMARY KEY,
    account_number   VARCHAR(64)  NOT NULL UNIQUE,
    customer_ref     VARCHAR(32)  NOT NULL,          -- links multiple accounts to one customer/business identity
    display_name     VARCHAR(100) NOT NULL,
    account_type     VARCHAR(20)  NOT NULL,          -- CUSTOMER | BUSINESS
    status           VARCHAR(20)  NOT NULL,          -- ACTIVE | BLOCKED | CLOSED
    default_currency VARCHAR(3)   NOT NULL DEFAULT 'INR',
    created_at       TIMESTAMP    NOT NULL,
    updated_at       TIMESTAMP    NOT NULL
);

CREATE INDEX idx_accounts_customer_ref ON accounts (customer_ref);

-- Added 2026-08-06: demo card registry. PCI-safe by construction - never a column
-- for full PAN or CVV. Only masked/tokenized references are ever stored.
CREATE TABLE cards (
    id              CHAR(36)     NOT NULL PRIMARY KEY,
    customer_ref    VARCHAR(32)  NOT NULL,
    card_brand      VARCHAR(20)  NOT NULL,           -- VISA | MASTERCARD (demo only)
    masked_pan      VARCHAR(24)  NOT NULL,           -- e.g. '**** **** **** 4242'
    last4           CHAR(4)      NOT NULL,
    expiry_month    TINYINT      NOT NULL,
    expiry_year     SMALLINT     NOT NULL,
    cardholder_name VARCHAR(100) NOT NULL,
    token_ref       VARCHAR(100) NOT NULL UNIQUE,     -- fake tokenizer reference, e.g. 'tok_demo_card_4242'
    status          VARCHAR(20)  NOT NULL,            -- ACTIVE | BLOCKED
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_cards_customer_ref ON cards (customer_ref);

-- Added 2026-08-06: fixed/seeded FX rates (no live FX calls). Business always
-- settles in INR (rate_to_inr = 1 unit of `currency` in INR).
CREATE TABLE exchange_rates (
    id           CHAR(36)      NOT NULL PRIMARY KEY,
    currency     VARCHAR(3)    NOT NULL UNIQUE,
    rate_to_inr  DECIMAL(18,8) NOT NULL,
    effective_at TIMESTAMP     NOT NULL,
    source       VARCHAR(40)   NOT NULL DEFAULT 'SEEDED_FIXED_RATE'
);

CREATE TABLE payments (
    id                  CHAR(36)        NOT NULL PRIMARY KEY,
    idempotency_key     VARCHAR(255)    NOT NULL UNIQUE,
    source_account      VARCHAR(64)     NOT NULL,
    destination_account VARCHAR(64)     NOT NULL,
    amount              DECIMAL(18,2)   NOT NULL,
    currency            VARCHAR(3)      NOT NULL,
    status              VARCHAR(20)     NOT NULL,
    error_code          VARCHAR(64)     NULL,
    type                VARCHAR(10)     NOT NULL,
    original_payment_id CHAR(36)        NULL,
    -- Added 2026-08-05 (spec.md Section 7, v2.2): extensible payment method tag.
    payment_method      VARCHAR(20)     NOT NULL DEFAULT 'BANK_TRANSFER',
    -- Added 2026-08-05 (spec.md Section 7/8.1 rule 6, v2.2): refund approval gate.
    -- Always NULL for type = PAYMENT rows; only ever set on type = REFUND rows.
    approval_status     VARCHAR(20)     NULL,
    approved_by         VARCHAR(64)     NULL,
    approved_at         TIMESTAMP       NULL,
    rejection_reason    VARCHAR(255)    NULL,
    -- Added 2026-08-06 (bank-grade validation + multi-currency, settle-in-INR):
    -- frozen at creation time, never recomputed on later transitions.
    settlement_currency   VARCHAR(3)    NOT NULL DEFAULT 'INR',
    fx_rate_to_inr        DECIMAL(18,8) NOT NULL DEFAULT 1.00000000,
    settlement_amount_inr DECIMAL(18,2) NOT NULL DEFAULT 0.00,
    -- Account/actor that initiated this payment or refund (spec.md segregation-of-duties rule).
    requested_by          VARCHAR(64)   NULL,
    -- Card snapshot fields, only set when payment_method = CARD. No FK to `cards`
    -- to keep historical payment rows intact even if the card record changes.
    card_id                CHAR(36)     NULL,
    card_last4             CHAR(4)      NULL,
    card_brand             VARCHAR(20)  NULL,
    created_at          TIMESTAMP       NOT NULL,
    updated_at          TIMESTAMP       NOT NULL,
    CONSTRAINT fk_payments_original_payment
        FOREIGN KEY (original_payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_type ON payments (type);
CREATE INDEX idx_payments_source_account ON payments (source_account);
CREATE INDEX idx_payments_destination_account ON payments (destination_account);
CREATE INDEX idx_payments_created_at ON payments (created_at);
CREATE INDEX idx_payments_original_payment_id ON payments (original_payment_id);

CREATE TABLE payment_status_history (
    id           CHAR(36)      NOT NULL PRIMARY KEY,
    payment_id   CHAR(36)      NOT NULL,
    from_status  VARCHAR(20)   NULL,
    to_status    VARCHAR(20)   NOT NULL,
    changed_at   TIMESTAMP     NOT NULL,
    triggered_by VARCHAR(32)   NOT NULL,
    note         VARCHAR(255)  NULL,
    -- Insertion-order tiebreaker: changed_at alone is second-precision and multiple
    -- transitions can land in the same second, which breaks "oldest first" ordering.
    seq          BIGINT        NOT NULL AUTO_INCREMENT UNIQUE,
    CONSTRAINT fk_history_payment
        FOREIGN KEY (payment_id) REFERENCES payments (id)
);

CREATE INDEX idx_history_payment_id ON payment_status_history (payment_id, changed_at, seq);
