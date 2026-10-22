CREATE TABLE reconciliation_runs (
    id                     BIGSERIAL PRIMARY KEY,
    run_id                 UUID        NOT NULL UNIQUE,
    scenario               VARCHAR(32) NOT NULL,
    status                 VARCHAR(16) NOT NULL,
    seed                   BIGINT      NOT NULL,
    started_at             TIMESTAMPTZ NOT NULL,
    completed_at           TIMESTAMPTZ,
    records_checked        INTEGER     NOT NULL DEFAULT 0,
    matched                INTEGER     NOT NULL DEFAULT 0,
    unmatched              INTEGER     NOT NULL DEFAULT 0,
    amount_mismatch_count  INTEGER     NOT NULL DEFAULT 0,
    missing_internal_count INTEGER     NOT NULL DEFAULT 0,
    missing_external_count INTEGER     NOT NULL DEFAULT 0,
    status_mismatch_count  INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_reconciliation_runs_started_at ON reconciliation_runs (started_at DESC);

CREATE TABLE reconciliation_items (
    id                 BIGSERIAL PRIMARY KEY,
    run_id             UUID        NOT NULL REFERENCES reconciliation_runs (run_id),
    payment_id         UUID,
    external_reference VARCHAR(64),
    classification     VARCHAR(32) NOT NULL,
    internal_amount    NUMERIC(19,4),
    external_amount    NUMERIC(19,4),
    internal_status    VARCHAR(32),
    external_status    VARCHAR(32),
    details            VARCHAR(512)
);

CREATE INDEX idx_reconciliation_items_run_id ON reconciliation_items (run_id);
