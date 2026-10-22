CREATE TABLE idempotency_records (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_hash    VARCHAR(64)  NOT NULL,
    payment_id      UUID,
    response_status INTEGER,
    response_body   TEXT,
    state           VARCHAR(16)  NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    expires_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records (expires_at);

CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    aggregate_type  VARCHAR(32)  NOT NULL,
    aggregate_id    VARCHAR(64)  NOT NULL,
    event_type      VARCHAR(64)  NOT NULL,
    event_version   INTEGER      NOT NULL DEFAULT 1,
    payload         JSONB        NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    attempts        INTEGER      NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ  NOT NULL,
    last_error      VARCHAR(1024),
    created_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ
);

CREATE INDEX idx_outbox_events_status_next_attempt ON outbox_events (status, next_attempt_at);
