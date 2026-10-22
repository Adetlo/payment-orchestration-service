# ADR 0003: Transactional outbox instead of dual writes

## Status

Accepted

## Context

Every externally visible payment state change must be persisted in PostgreSQL **and**
published to Kafka (`payment.events.v1`). The naive implementation writes to the database and
then calls the Kafka producer in the same request.

**Why the direct database + Kafka dual write is unsafe.** The two writes are not atomic:
there is no transaction that spans PostgreSQL and Kafka. If the database commit succeeds and
the Kafka send fails (broker unavailable, timeout, process crash between the two), the
payment exists but no consumer ever learns about it, so it stays in `RECEIVED` for ever. If
the send succeeds and the commit fails (constraint violation, deadlock, crash before commit),
consumers process an event for a payment that does not exist. Retrying the send to patch the
first case introduces duplicates and, worse, breaks ordering: two retries for the same payment
can be delivered out of order relative to a later event, so a consumer may see `Completed`
before `Validated`. Even without failures, a send that is issued before the transaction
commits is a partial-failure window, and one issued after commit is a lost-event window.
Partial failure, loss of ordering and lack of atomicity are inherent to dual writes, not bugs
that can be patched with retries.

Alternatives considered: Debezium/CDC on the `outbox_events` table (correct and common in
production, but adds Kafka Connect to a demo stack); Kafka transactions plus database
transaction with best-effort ordering (still not atomic across systems); publishing from a
`@TransactionalEventListener(AFTER_COMMIT)` (loses the event if the process dies after
commit and before send).

## Decision

Implement the **transactional outbox** pattern (DESIGN.md 4.4 step 2):

1. The state transition, the `payment_events` row, the `audit_entries` row and an
   `outbox_events` row (status `NEW`, JSON envelope payload) are written in **one** database
   transaction.
2. An `OutboxPublisher` scheduled every 500 ms selects up to 50 `NEW` rows with
   `FOR UPDATE SKIP LOCKED`, sends each to Kafka with key = `paymentId`, and marks it
   `PUBLISHED` once the broker acknowledges.
3. On send failure the row's `attempts` is incremented and `nextAttemptAt` set with backoff
   1 s, 2 s, 4 s, 8 s, 16 s; after 5 attempts the row is marked `FAILED`, surfaced by the
   `outbox_failed` gauge and visible in `/system/stats`.

Delivery is therefore **at least once**; consumers are idempotent (ADR 0004).

## Consequences

* An event is published if and only if its transaction committed. No lost events, no phantom
  events.
* Per-payment ordering is preserved: rows are selected in insertion order and keyed by
  payment, so they land on the same partition in order.
* Publication latency is bounded by the polling interval (up to ~500 ms) plus Kafka latency.
* Duplicates are possible (crash after send, before marking `PUBLISHED`), so every consumer
  re-reads the payment and ignores events whose status no longer applies.
* The `outbox_events` table grows; the demo keeps `PUBLISHED` rows for inspection, a
  production deployment would archive or delete them.
* Observable: `outbox_pending`, `outbox_failed`, `outbox_published_total`,
  plus the Grafana "Outbox and Kafka" row.
