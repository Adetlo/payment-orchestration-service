# ADR 0002: PostgreSQL as the system of record

## Status

Accepted

## Context

Payments, their events, the audit trail, idempotency records, the outbox and reconciliation
results must be stored durably with strong consistency. Several of the design's guarantees
depend on multi-row atomic writes (DESIGN.md 4.3, 4.4) and on unique constraints enforced by
the database (DESIGN.md 4.5). Monetary amounts must be exact.

Alternatives considered: an event store only (no query model for the UI and the API list
endpoint), MongoDB (weaker multi-document transaction ergonomics and no `SKIP LOCKED`),
H2 for simplicity (does not exercise the real locking and constraint behaviour).

## Decision

Use **PostgreSQL 16** as the only system of record. Schema managed by **Flyway**
(`V1__payments.sql` onwards) with Hibernate set to validate. Amounts are `NUMERIC(19,4)` with
`CHAR(3)` currency codes and are mapped to `BigDecimal`, never floating point.

Database features relied on explicitly:

* Transactions spanning `payments`, `payment_events`, `audit_entries`, `outbox_events` and
  `idempotency_records` so a state transition and its side effects commit or roll back together.
* Unique constraint on `idempotency_records.idempotency_key` as the idempotency source of truth.
* `SELECT ... FOR UPDATE SKIP LOCKED` for the outbox publisher and the retry scheduler, which
  lets several instances drain the same tables without double-processing.
* Optimistic locking (`version` column) on `payments`.
* A trigger (`audit_entries_append_only`) that rejects `UPDATE`/`DELETE` on the audit table,
  so append-only is enforced below the application.
* Indexes listed in DESIGN.md 4.8 to support the list/filter endpoints and the pollers.

## Consequences

* One durable store, one backup story, one place where invariants are enforced.
* Pollers (outbox every 500 ms, retries every 1 s) add light constant load; acceptable for the
  demo and typical for this pattern.
* Horizontal scaling of the API is possible because coordination happens in the database;
  scaling the database itself is out of scope for the project.
* Tests use a real PostgreSQL container (Testcontainers) because the behaviour under test
  depends on PostgreSQL-specific locking and constraints.
