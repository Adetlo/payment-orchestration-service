# ADR 0004: Kafka events with a versioned envelope, keyed by payment, with a dead-letter topic

## Status

Accepted

## Context

Payment processing is asynchronous: the API accepts the instruction and returns `201`, and
the rest of the lifecycle (validation, settlement, retries) happens in the background. The
design calls for an event stream that other services could subscribe to and that drives the
service's own consumer (DESIGN.md 4.4, 4.6).

Alternatives considered: an in-process queue (no durability, no replay, not representative),
PostgreSQL as the only queue (works for the retry scheduler, but hides the integration
problems the project wants to demonstrate), RabbitMQ (fine, but Kafka's partition ordering
maps directly onto the per-payment ordering requirement).

## Decision

* **Topics**: `payment.events.v1` for all payment lifecycle events,
  `reconciliation.events.v1` for `ReconciliationCompleted`, and `payment.events.v1.dlt` as the
  dead-letter topic. The version suffix in the topic name allows a breaking envelope change to
  ship as `v2` alongside `v1`.
* **Envelope** (JSON): `eventId`, `eventType`, `eventVersion` (currently 1), `occurredAt`,
  `paymentId`, `correlationId` and a typed `data` object. Account numbers are never in the
  payload, only a masked form (`payerAccountMasked: "GB**...1234"`). Consumers must tolerate
  unknown fields; additive changes bump nothing, breaking changes bump `eventVersion` and
  the topic.
* **Key = `paymentId`**, so all events for one payment are on one partition and delivered in
  order, while different payments process in parallel across partitions (3 in the demo).
* **Consumer** `PaymentProcessingConsumer` (group `payment-processor`) is idempotent: it
  re-reads the payment and ignores events whose status no longer applies, which makes
  at-least-once delivery from the outbox safe.
* **Error handling**: Spring Kafka `DefaultErrorHandler` with 3 attempts and 500 ms backoff,
  then `DeadLetterPublishingRecoverer` moves the record to `payment.events.v1.dlt`. Dead
  letters increment `payment_dead_letter_total` and are logged with the `paymentId`. The
  `poison-message` demo failure mode exercises this path.
* **Single-node KRaft** broker in docker compose (no ZooKeeper), replication factor 1 and
  auto topic creation enabled for the demo; a real deployment would create topics explicitly
  with replication factor 3.

## Consequences

* Ordering and parallelism follow from the key choice; no custom sequencing code.
* The dead-letter topic turns a poison message from a stuck partition into an observable,
  recoverable event (it can be inspected and replayed by hand).
* At-least-once delivery means consumers must stay idempotent; the state machine's transition
  table makes "does this still apply" a cheap check.
* The demo broker configuration is not durable or highly available by design
  (see README "Limitations").
