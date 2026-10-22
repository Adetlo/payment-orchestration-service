# Benchmarks

> **Synthetic local benchmark, not production capacity testing.** The numbers below were produced on
> a laptop against the Docker Compose stack, with the client (k6) and all seven containers sharing
> the same machine. They show that the service behaves well under modest concurrent load and that the
> asynchronous pipeline keeps up; they say nothing about production hardware, network latency, real
> payment networks or multi-node Kafka.

## Scenario

`perf/k6/payments-load.js` posts valid synthetic payments (unique `Idempotency-Key` per request,
random currency and amount) with a ramping-VUs profile: 0 -> 20 virtual users over 30 s, hold 20 VUs
for 60 s, ramp down over 10 s. Thresholds: `http_req_failed < 1 %`, `p(95) < 500 ms`, checks > 99 %.

The per-IP rate limiter (200 requests per minute, see ADR 0007) is disabled for the run through the
compose overlay, otherwise k6 would measure the limiter rather than the service:

```bash
docker compose -f docker-compose.yml -f perf/docker-compose.perf.yml up -d backend
k6 run --no-usage-report perf/k6/payments-load.js
```

## Environment

| Item | Value |
|---|---|
| Date | 2026-10-22 |
| Commit | the tree released as `v1.0.0` |
| Machine | Apple Silicon laptop, 8 CPU cores, 16 GB RAM, macOS 26 (Darwin 25.6) |
| Docker VM | Colima / Lima, Docker Engine 29.5.2, 4 CPUs, 6 GB; memory limits from `docker-compose.yml` (backend 1 GB, Kafka 1 GB) |
| JVM | Eclipse Temurin 21.0.13 inside `eclipse-temurin:21-jre-alpine`, `-XX:MaxRAMPercentage=70` |
| k6 | v2.3.0 (darwin/arm64) |
| Command | `k6 run --no-usage-report perf/k6/payments-load.js` (defaults: 20 VUs, 60 s hold) |

## Results

| Metric | Value |
|---|---|
| Iterations (payments created) | 14,223 |
| Requests per second | 142.2 |
| `http_req_failed` | 0.000 % |
| Checks passed | 100 % |
| `http_req_duration` avg / med / p90 / p95 / p99 / max (ms) | 11.15 / 7.12 / 19.28 / 27.57 / 53.65 / 757.09 |
| `payment_create_duration` p95 (ms) | 27.57 |
| 4xx responses | 0 |
| Thresholds | all PASS (`checks`, `http_req_duration p(95)<500`, `http_req_failed rate<0.01`, `payment_create_duration p(95)<500`) |

The raw k6 summary is committed as `perf/results/sample-summary-2026-10-22.json`.

## Asynchronous pipeline during the run

The POST only writes the payment and its outbox row; validation, settlement and completion happen
through Kafka afterwards. Observed with Prometheus queries about 20 s after the run ended:

| Measure | Value |
|---|---|
| Payments received / completed (`payment_received_total`, `payment_completed_total`) | 14,223 / 14,223 |
| Payments still non-terminal after the run | 0 (`/api/v1/system/stats`) |
| Outbox rows pending / failed after the run | 0 / 0 |
| Peak outbox backlog during the run (`max_over_time(outbox_pending[10m])`) | 221 rows |
| End-to-end processing time RECEIVED -> COMPLETED, p50 / p95 / p99 | 0.38 s / 0.80 s / 1.39 s |
| Kafka events published (`outbox_published_total`) | 42,721 (three events per payment) |

## Observations

* The API path stays below 30 ms at p95 with 20 concurrent clients; the single slow request
  (757 ms max) coincided with the first G1 young collections after the ramp-up.
* The outbox publisher (500 ms poll, batch 50) is the throttle of the asynchronous path: the backlog
  peaked at 221 rows and drained within seconds of the ramp-down. A larger batch or a shorter poll
  interval would lower the p95 end-to-end time; both are configuration properties.
* Kafka was the busiest container (about 50 % of one CPU in the VM); PostgreSQL and the backend JVM
  stayed well inside their limits (backend about 450 MB RSS of 1 GB).
* No errors, no dead letters and no retries occurred because failure injection is only triggered by
  the `X-Demo-Failure` header, which the load script does not send.
