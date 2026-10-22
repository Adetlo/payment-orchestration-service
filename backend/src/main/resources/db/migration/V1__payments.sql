CREATE TABLE payments (
    payment_id        UUID PRIMARY KEY,
    payer_account     VARCHAR(34)   NOT NULL,
    payee_account     VARCHAR(34)   NOT NULL,
    amount            NUMERIC(19,4) NOT NULL,
    currency          CHAR(3)       NOT NULL,
    reference         VARCHAR(35)   NOT NULL,
    status            VARCHAR(32)   NOT NULL,
    failure_reason    VARCHAR(512),
    retry_count       INTEGER       NOT NULL DEFAULT 0,
    max_retries       INTEGER       NOT NULL DEFAULT 3,
    next_retry_at     TIMESTAMPTZ,
    demo_failure_mode VARCHAR(32),
    idempotency_key   VARCHAR(128),
    correlation_id    VARCHAR(64)   NOT NULL,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0
);

CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_created_at ON payments (created_at DESC);
CREATE INDEX idx_payments_currency_created_at ON payments (currency, created_at);
CREATE INDEX idx_payments_retry_due ON payments (next_retry_at) WHERE status = 'RETRY_PENDING';

CREATE TABLE payment_events (
    id             BIGSERIAL PRIMARY KEY,
    event_id       UUID        NOT NULL UNIQUE,
    payment_id     UUID        NOT NULL REFERENCES payments (payment_id),
    event_type     VARCHAR(64) NOT NULL,
    event_version  INTEGER     NOT NULL DEFAULT 1,
    occurred_at    TIMESTAMPTZ NOT NULL,
    from_status    VARCHAR(32),
    to_status      VARCHAR(32) NOT NULL,
    details        JSONB       NOT NULL DEFAULT '{}'::jsonb,
    correlation_id VARCHAR(64) NOT NULL
);

CREATE INDEX idx_payment_events_payment_occurred ON payment_events (payment_id, occurred_at);
