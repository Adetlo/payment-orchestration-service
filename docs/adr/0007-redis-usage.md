# ADR 0007: Redis for a short idempotency lock and rate limiting only

## Status

Accepted

## Context

Redis is easy to over-use. Including it in the stack must be justified by a concrete need,
and the service must keep working when it is down, otherwise the demo adds an availability
dependency for little benefit.

Alternatives considered: no Redis (idempotency still correct via the DB constraint, but
concurrent duplicates race to the database and rate limiting would need a DB table or an
in-memory limiter that does not work across instances); PostgreSQL advisory locks (work, but
hold a connection for the request duration); an in-memory bucket (per instance only).

## Decision

Redis is used for exactly two things (DESIGN.md 4.9):

1. **Idempotency lock**: `SET idem:{key} NX PX 10000` serialises concurrent requests with the
   same `Idempotency-Key` before they reach the database, turning a constraint-violation race
   into a clean `409 idempotency-request-in-progress` with `Retry-After: 1`. The lock is an
   aid; the database unique constraint remains the source of truth (ADR 0005).
2. **Rate limiting**: a fixed-window counter per client IP on `POST /api/v1/payments`
   (default 200 requests per minute, disabled in the `test` profile) that works across
   several API instances.

Why Redis is justified: both needs are cross-instance, short-lived, high-frequency and
tolerate loss, which is precisely the profile of a shared in-memory store, and they would be
awkward or expensive in PostgreSQL.

**When Redis is down**: the service degrades, it does not fail.

* The idempotency lock is skipped with a warning; correctness is preserved by the constraint.
  Concurrent duplicates may now surface as a brief `409 idempotency-request-in-progress`
  after the database rejects the second insert, or as a replay once the first completes.
* Rate limiting is skipped with a warning (fail-open for availability; the limit is a
  protection against accidental floods, not a security boundary).
* The `redis` component in `GET /api/v1/system/health` reports `DEGRADED` and the overall
  status becomes `DEGRADED`, visible on the UI health page and the dashboard.
* No data lives only in Redis, so nothing is lost when it restarts; the demo even runs it
  with persistence disabled.

## Consequences

* One more container (128 MB) and one more health indicator; small cost.
* The service stays correct and available without Redis; only the ergonomics of concurrent
  duplicates and flood protection degrade.
* The dependency's scope is written down, so future features that "could just use Redis"
  have to argue against this record first.
