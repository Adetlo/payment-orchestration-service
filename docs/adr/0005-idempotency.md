# ADR 0005: Idempotency keys backed by a database unique constraint

## Status

Accepted

## Context

Clients retry `POST /api/v1/payments` after timeouts. Without idempotency a retry creates a
second payment, which in a payment system means paying twice. The design (DESIGN.md 4.5)
requires an `Idempotency-Key` header, replay of the original response, and a `409` when the
key is reused with a different body, including under concurrent requests.

Alternatives considered: Redis as the only idempotency store (fast, but loses records on
restart and makes correctness depend on cache availability); application-level in-memory
locks (do not work across instances); deriving a key from the request body (silently merges
legitimately identical instructions).

## Decision

* The **database unique constraint** on `idempotency_records.idempotency_key` is the **source
  of truth**. A record is inserted in state `IN_PROGRESS` before the payment is created; a
  `DataIntegrityViolation` on insert means another request already owns the key and the
  handler re-reads the record instead of creating a payment.
* The **request hash** is SHA-256 over the canonical JSON body (sorted keys, no insignificant
  whitespace), so formatting differences do not cause false conflicts and semantic
  differences do.
* A **Redis lock** (`SET idem:{key} NX PX 10000`) is taken first as a **serialisation aid**: it
  makes concurrent duplicates wait or fail fast with `409 idempotency-request-in-progress` and
  `Retry-After: 1` instead of racing to the constraint. If Redis is unavailable the service
  logs a warning and proceeds; correctness still holds because the constraint is unchanged
  (fail-safe, never fail-open on duplicates).
* Outcomes: same key + same hash + `COMPLETED` record returns the stored status and body with
  `Idempotent-Replayed: true`; same key + different hash returns
  `409 idempotency-key-reuse`; same key while the first request is still running returns
  `409 idempotency-request-in-progress`.
* Records **expire after 24 hours** (an hourly cleanup job deletes expired rows), bounding
  table growth; a key reused after expiry is treated as new.

## Consequences

* Exactly one payment per key regardless of concurrency or Redis availability, verified by a
  20-thread concurrent test.
* Replays are byte-for-byte identical responses, so clients can treat them as the original.
* Storing the response body makes the record larger; acceptable for a 24 h window.
* The lock's 10 s TTL bounds the worst case if a request dies holding it.
* Metrics `idempotency_replay_total` and `idempotency_conflict_total` make misbehaving
  clients visible on the dashboard.
