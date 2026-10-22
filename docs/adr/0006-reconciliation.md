# ADR 0006: Reconciliation against a synthetic settlement feed

## Status

Accepted

## Context

A payment service must prove that its internal view agrees with the external settlement
system's view: every completed payment was settled for the same amount, nothing was settled
that the service does not know about, and nothing the service completed is missing
externally. Real settlement files are proprietary and unavailable to a portfolio project.

Alternatives considered: skip reconciliation (leaves out one of the most important
operational controls); import a fixed CSV fixture (not reproducible against a changing
database); fuzz the feed randomly (non-deterministic screenshots and tests).

## Decision

* A `SyntheticSettlementFeed` builds the "external" records **from the current COMPLETED and
  FAILED payments**, so the feed always matches the database state the demo happens to be in.
* `POST /api/v1/reconciliation/run` runs synchronously (the data set is small) and returns
  `200` with the run summary. Two scenarios (DESIGN.md 4.4 step 7):
  * `clean` (default): every record matches.
  * `with-discrepancies`: a **seeded** random perturbation (`seed` in the request, default 42)
    injects exactly one `AMOUNT_MISMATCH`, one `MISSING_INTERNAL` (extra external record),
    one `MISSING_EXTERNAL` (completed payment dropped from the feed) and one `STATUS_MISMATCH`
    (a FAILED payment reported as settled), when enough payments exist.
* Each run persists a `reconciliation_runs` row with counts per classification and a
  `reconciliation_items` row per record (`MATCHED`, `AMOUNT_MISMATCH`, `MISSING_INTERNAL`,
  `MISSING_EXTERNAL`, `STATUS_MISMATCH`), writes an audit entry, publishes
  `ReconciliationCompleted` on `reconciliation.events.v1` and increments
  `reconciliation_runs_total` and `reconciliation_mismatch_total{classification}`.
* Matching is by external reference to `paymentId`, then by amount and status; the item
  stores both sides (`internalAmount`/`externalAmount`, `internalStatus`/`externalStatus`) so a
  reviewer can see why it was classified.

## Consequences

* Deterministic: the same database state and seed produce the same run, which keeps the
  UI screenshots, `scripts/seed-demo.sh` and the integration tests stable.
* The classification set is the same one real reconciliation tooling uses, so the model
  transfers; the feed itself does not.
* Runs are synchronous and in-memory, which is fine for hundreds of payments and would need
  batching and an asynchronous job for real volumes.
* Nothing is auto-corrected: reconciliation reports, people (or a later workflow) resolve.
