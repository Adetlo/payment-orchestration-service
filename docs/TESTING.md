# Testing

Testing is the main engineering evidence in this repository. The backend suite runs on every push
in CI against real PostgreSQL, Kafka and Redis containers; nothing is mocked at the infrastructure
boundary except where a unit test deliberately isolates one class.

## Strategy

| Layer | What is proved | How |
|---|---|---|
| Domain unit tests | `Money` scale and currency rules, `AccountReference` format, the full `PaymentStatus` transition table, event envelope serialisation, canonical request hashing | Plain JUnit 5 + AssertJ, no Spring context |
| Application / service tests | Idempotency decision logic, retry backoff schedule, reconciliation classification, outbox publisher behaviour on send failure | Spring slices with Mockito for the ports (`SettlementGateway`, Kafka template) |
| Repository / JPA tests | Flyway migrations apply cleanly, `SKIP LOCKED` batch selection, audit append-only trigger (UPDATE and DELETE rejected), indexes used by the list query | `@DataJpaTest` against Testcontainers PostgreSQL |
| API tests | Every endpoint, validation problem bodies, RFC 7807 shape, security headers, `X-Request-Id`/`X-Correlation-Id` echo, Swagger/OpenAPI availability, actuator metrics | `MockMvc` with the full application context |
| Idempotency | Replay returns the byte-identical response, payload mismatch returns 409, in-progress returns 409 with `Retry-After`, **20 concurrent threads with the same key create exactly one payment**, behaviour with the Redis lock held and with Redis unavailable | Full context + Testcontainers PostgreSQL and Redis |
| Outbox | Payment state change and outbox row are written in the same transaction (a forced rollback leaves nothing behind), publisher marks PUBLISHED, send failure follows the 1-2-4-8-16 s backoff then FAILED | Full context + Testcontainers Kafka |
| Kafka / event-driven processing | RECEIVED -> VALIDATED -> PROCESSING -> COMPLETED end to end through the real broker; consumer idempotency on redelivery; poison message delivered three times then dead-lettered to `payment.events.v1.dlt` | Testcontainers Kafka + a test consumer on the topics |
| Failure injection and retries | `settlement-timeout` -> RETRY_PENDING -> COMPLETED with `retryCount` 2; `settlement-rejected` -> FAILED with reason; retries exhausted -> FAILED; injection ignored and audited when the `demo` profile is off | Full context, Awaitility polling |
| Cancellation | Allowed from RECEIVED, VALIDATED, RETRY_PENDING; 409 from terminal states | API + service tests |
| Reconciliation | `clean` scenario matches all; `with-discrepancies` produces exactly one AMOUNT_MISMATCH, MISSING_INTERNAL, MISSING_EXTERNAL and STATUS_MISMATCH; run summary, items and metrics | Full context |
| Observability | Metric names on `/actuator/prometheus`, health components in `/api/v1/system/health`, stats shape | API tests |
| Frontend | Status pill mapping, string-based money formatting, API client problem parsing, create form validation / 201 / replay header / 409, dashboard aggregates, timeline ordering, health cards, polling hook (including a React 18 StrictMode regression) | Vitest + Testing Library + jsdom |

Testcontainers images are started once per JVM by `IntegrationTestSupport` (`postgres:16-alpine`,
`apache/kafka:3.9.1`, `redis:7-alpine`) and shared by all integration tests through
`@DynamicPropertySource`. Integration tests run with the `test,demo` profiles so that the failure
injection modes can be exercised.

## Commands

```bash
# backend: unit + integration + Spotless + SpotBugs + JaCoCo (needs Docker for Testcontainers)
cd backend && mvn -B -ntp verify

# unit tests only (no Docker)
cd backend && mvn -B -ntp test

# frontend
cd frontend && npm ci && npm run lint && npm run typecheck && npm run format:check && npm test -- --run
```

## Measured results

Run on 2026-10-22 on macOS (Apple Silicon, 8 cores, 16 GB) with Temurin JDK 21.0.13, Maven 3.9.16
and Docker Engine 29.5.2 in a Colima VM (4 CPUs, 6 GB), from `mvn -B -ntp verify`:

```
[INFO] Tests run: 136, Failures: 0, Errors: 0, Skipped: 0        (surefire, 21 classes)
[INFO] Tests run: 41,  Failures: 0, Errors: 0, Skipped: 0        (failsafe, 8 classes)
[INFO] BugInstance size is 0                                     (SpotBugs, 125 classes, effort max)
[INFO] BUILD SUCCESS
```

| Suite | Tests | Failures | Errors | Skipped | Runtime |
|---|---|---|---|---|---|
| Backend unit (Surefire, `*Test`) | 136 | 0 | 0 | 0 | JUnit 5 |
| Backend integration (Failsafe, `*IT`) | 41 | 0 | 0 | 0 | Testcontainers PostgreSQL + Kafka + Redis |
| Frontend (Vitest) | 33 | 0 | 0 | 0 | jsdom |
| **Total** | **210** | 0 | 0 | 0 | |

## Coverage (JaCoCo, backend)

From `backend/target/site/jacoco/jacoco.csv` after `mvn verify`:

| Scope | Instructions | Branches | Lines |
|---|---|---|---|
| overall | 96.2 % (267 of 7,061 missed) | 89.0 % (34 of 310 missed) | 95.8 % (69 of 1,641 missed) |

The report is uploaded as the `jacoco-report` artifact of every CI run.

## Static analysis and scanning

| Check | Result (local, 2026-10-22) |
|---|---|
| Spotless (google-java-format, AOSP style) | clean, enforced in `verify` |
| SpotBugs (effort max, threshold medium, fail on error) | 0 findings on 125 classes |
| ESLint (typescript-eslint type-checked, react-hooks, jsx-a11y) + `tsc --noEmit` strict | clean |
| Prettier | clean |
| ShellCheck on `scripts/*.sh` | clean |
| Trivy, CodeQL | run in CI; results linked from `docs/EVIDENCE.md` |

## What is not covered

* The Redis rate limiter is covered functionally, not under sustained concurrency.
* The Grafana dashboard queries and the Prometheus scrape configuration are validated by running
  the stack, not by automated tests.
* The nginx configuration of the frontend image is smoke-tested (`/healthz`, SPA fallback, headers)
  by hand and by the `compose-smoke` CI job, not by unit tests.
* The Playwright project only produces screenshots; it is not a functional end-to-end suite.
* No contract tests against a real payment network exist, because there is none: the settlement
  gateway is a simulator.
