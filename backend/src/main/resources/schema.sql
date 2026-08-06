-- Canonical schema (spec.md Section 7). Do not amend without updating the spec first.

DROP TABLE IF EXISTS payment_status_history;
DROP TABLE IF EXISTS payments;

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
