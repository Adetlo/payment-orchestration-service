CREATE TABLE audit_entries (
    id          BIGSERIAL PRIMARY KEY,
    payment_id  UUID,
    action      VARCHAR(64) NOT NULL,
    actor       VARCHAR(16) NOT NULL,
    details     JSONB       NOT NULL DEFAULT '{}'::jsonb,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_audit_entries_payment_occurred ON audit_entries (payment_id, occurred_at);

CREATE OR REPLACE FUNCTION audit_entries_reject_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_entries is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_entries_append_only
    BEFORE UPDATE OR DELETE ON audit_entries
    FOR EACH ROW EXECUTE FUNCTION audit_entries_reject_mutation();
