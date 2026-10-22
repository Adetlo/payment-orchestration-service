# Engineering evidence

Objective, measured evidence produced by this repository. Every number here was taken from a real
run; nothing is estimated. Where a figure comes from CI, the row names the workflow so the latest
run can be checked on the Actions tab.

## Summary

* 210 automated tests (136 backend unit, 41 backend integration on real PostgreSQL, Kafka and Redis
  containers, 33 frontend), all passing; backend coverage 96.2 % instructions / 89.0 % branches.
* Local synthetic benchmark: 14,223 payments created in 100 s at 142 req/s, p50 7.1 ms, p95 27.6 ms,
  0 % errors, and every one of them completed through Kafka with the outbox drained to zero.
* Static analysis clean: Spotless, SpotBugs (0 findings), ESLint, TypeScript strict, Prettier,
  ShellCheck. Trivy and CodeQL run in CI.
* Containers run as non-root; the full seven-service stack starts with one command and the scripted
  demo exercises replay, conflict, retry, failure, cancel and reconciliation against it.

## Tests

| Suite | Count | Result | Source |
|---|---|---|---|
| Backend unit (Surefire, 21 classes) | 136 | 0 failures, 0 errors, 0 skipped | `backend/target/surefire-reports` |
| Backend integration (Failsafe, 8 classes, Testcontainers) | 41 | 0 failures, 0 errors, 0 skipped | `backend/target/failsafe-reports` |
| Frontend (Vitest, 9 files) | 33 | all passing | `frontend/src/test` |
| **Total** | **210** | | |

Coverage (JaCoCo, `backend/target/site/jacoco/jacoco.csv`): 96.2 % instructions (267 of 7,061
missed), 89.0 % branches (34 of 310 missed), 95.8 % lines (69 of 1,641 missed). The HTML report is
the `jacoco-report` artifact of the `Backend (Maven verify)` CI job. Categories and strategy:
[TESTING.md](TESTING.md).

## Idempotency

Tests `IdempotencyIT` (replay, payload mismatch, in-progress, lock held, Redis unavailable) and the
20-thread concurrent test assert that exactly one `payments` row exists per key. Live output from
`scripts/demo.sh` on the compose stack (2026-10-22):

```
==> 2. Replay the same request (same Idempotency-Key, same body)
    HTTP 201   Idempotent-Replayed: true   same paymentId, byte-identical body

==> 3. Reuse the key with a different amount
    HTTP 409   application/problem+json
    {"type":"about:blank#idempotency-key-reuse","title":"Idempotency key reused","status":409,
     "detail":"Idempotency-Key '...' was already used with a different request payload"}
```

Full responses: [API_EXAMPLES.md](API_EXAMPLES.md) sections 1 to 3.

## Retry and failure handling

`scripts/demo.sh` section 4, payment created with `X-Demo-Failure: settlement-timeout`:

```
RECEIVED -> RETRY_PENDING (attempt 1) -> PROCESSING -> RETRY_PENDING (attempt 2) -> PROCESSING -> COMPLETED
retryCount: 2, maxRetries: 3, failureReason: null
```

`settlement-rejected` ends in `FAILED` with `failureReason: "settlement network rejected the payment
(demo)"`; `poison-message` is delivered three times by the consumer, published to
`payment.events.v1.dlt`, counted in `payment_dead_letter_total` and audited as `payment.dead-lettered`.
The 8-event timeline of the retried payment is shown in `docs/images/04-payment-events.png`.

## Reconciliation

`POST /api/v1/reconciliation/run {"scenario":"with-discrepancies","seed":42}` on the seeded stack:

```json
{"recordsChecked":15,"matched":11,"unmatched":4,
 "byClassification":{"MATCHED":11,"AMOUNT_MISMATCH":1,"MISSING_INTERNAL":1,"MISSING_EXTERNAL":1,"STATUS_MISMATCH":1}}
```

Exactly one item of each discrepancy class, as the scenario promises; the `clean` scenario matches
everything. Items are listed in [API_EXAMPLES.md](API_EXAMPLES.md) section 9.

## Observability

`/actuator/prometheus` exposes the business meters (excerpt after the demo run):

```
payment_received_total 5.0   payment_completed_total 2.0   payment_failed_total 1.0
payment_rejected_total 1.0   payment_cancelled_total 1.0   payment_retry_total 3.0
payment_dead_letter_total 1.0   payment_processing_duration_seconds_count 5
outbox_pending 1.0   outbox_failed 0.0   outbox_published_total 17.0
idempotency_replay_total 2.0   idempotency_conflict_total 1.0
reconciliation_runs_total 1.0   reconciliation_mismatch_total{classification="AMOUNT_MISMATCH"} 1.0
```

JSON log line with correlation context (profile `docker`):

```json
{"@timestamp":"2026-10-22T16:22:48.334Z","message":"Payment 5b5aecb6-... received (125.50 GBP)",
 "logger_name":"io.github.adetlo.payments.application.PaymentService","level":"INFO",
 "requestId":"c9a8b723-...","correlationId":"5be837dc-...","service":"payment-orchestration-service"}
```

Dashboards: Prometheus (`docs/images/08-prometheus-metrics.png`) and the provisioned Grafana board
(`docker/grafana/dashboards/payments.json`).

## Benchmark

k6 v2.3.0, 20 virtual users, 100 s profile, local Docker Compose stack on an 8-core Apple Silicon
laptop (Colima VM 4 CPUs / 6 GB): 14,223 payments, 142.2 req/s, `http_req_duration` p50 7.12 ms /
p95 27.57 ms / p99 53.65 ms, 0.000 % failed, all thresholds passed; asynchronous completion p50
0.38 s / p95 0.80 s, peak outbox backlog 221 rows, 0 pending after the run. Synthetic local
benchmark only. Details and caveats: [BENCHMARKS.md](BENCHMARKS.md); raw summary:
`perf/results/sample-summary-2026-10-22.json`.

## Build, scanning and runtime

| Evidence | Result |
|---|---|
| `mvn -B -ntp verify` (tests, Spotless, SpotBugs, JaCoCo) | BUILD SUCCESS locally on 2026-10-22; repeated by the `Backend (Maven verify)` CI job |
| SpotBugs (effort max, threshold medium) | 0 findings on 125 classes |
| Frontend lint / typecheck / format / test / build | clean; `dist` 224 KB (43 kB app + 164 kB vendor + 13.5 kB CSS, 66 kB gzipped) |
| Docker images | backend `eclipse-temurin:21-jre-alpine` based, 469 MB; frontend `nginxinc/nginx-unprivileged:1.27-alpine` based, 76 MB |
| Containers run as non-root | backend `uid=10001(app) gid=10001(app)`; frontend `uid=101(nginx) gid=101(nginx)` (`docker compose exec ... id`) |
| `docker compose up --build` | all seven services healthy; API reports UP within seconds of the backend container starting |
| `scripts/demo.sh --no-start` and `scripts/seed-demo.sh` | exit 0 on the compose stack (2026-10-22) |
| CI workflow (backend, frontend, docker, compose-smoke, trivy, shellcheck) | see the CI badge in the README and the Actions tab; the run recorded for v1.0.0 is linked below once available |
| CodeQL (java-kotlin, javascript-typescript) | runs on push, pull request and weekly; results in the Security tab |
| Trivy vulnerability (CRITICAL, HIGH, unfixed ignored) and misconfiguration scans | part of the CI workflow |
| Dependabot | enabled for maven, npm (frontend, e2e), github-actions and docker |

## API endpoints, topics and migrations

* REST: `POST /api/v1/payments`, `GET /api/v1/payments`, `GET /api/v1/payments/{id}`,
  `POST /api/v1/payments/{id}/cancel`, `GET /api/v1/payments/{id}/events`,
  `POST /api/v1/reconciliation/run`, `GET /api/v1/reconciliation/runs`,
  `GET /api/v1/reconciliation/runs/{runId}`, `GET /api/v1/system/health`, `GET /api/v1/system/stats`;
  OpenAPI at `/v3/api-docs`, Swagger UI at `/swagger-ui.html`.
* Kafka topics: `payment.events.v1` (key = paymentId), `payment.events.v1.dlt`,
  `reconciliation.events.v1`; consumer group `payment-processor`.
* Flyway migrations: `V1__payments.sql`, `V2__idempotency_and_outbox.sql`, `V3__audit_entries.sql`
  (append-only trigger), `V4__reconciliation.sql`.

## Screenshots

The eight images in `docs/images/` and the social preview were generated on 2026-10-22 by
`scripts/capture-screenshots.sh` (Playwright, Chromium, 1440x900) from a freshly started compose
stack seeded by `scripts/seed-demo.sh`. They contain synthetic data only.

## CI run recorded for v1.0.0

<!-- CI-RUN: filled after the first green run on the published repository -->
