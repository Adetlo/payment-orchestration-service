# Architecture decision records

Short records of the decisions that shape this project. Each follows the same layout:
Status, Context, Decision, Consequences. The design contract they implement is
[`docs/DESIGN.md`](../DESIGN.md); the diagrams are in [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md).

| ADR | Title | Status |
|-----|-------|--------|
| [0001](0001-spring-boot.md) | Java 21 and Spring Boot 3 for the service | Accepted |
| [0002](0002-postgresql.md) | PostgreSQL as the system of record | Accepted |
| [0003](0003-transactional-outbox.md) | Transactional outbox instead of dual writes | Accepted |
| [0004](0004-kafka-events.md) | Kafka events with a versioned envelope, keyed by payment, with a dead-letter topic | Accepted |
| [0005](0005-idempotency.md) | Idempotency keys backed by a database unique constraint | Accepted |
| [0006](0006-reconciliation.md) | Reconciliation against a synthetic settlement feed | Accepted |
| [0007](0007-redis-usage.md) | Redis for a short idempotency lock and rate limiting only | Accepted |

Adding one: copy the layout of an existing record, number it sequentially, link it here, and
reference it from the README's "Key engineering decisions" section if it is user-visible.
