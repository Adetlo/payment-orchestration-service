# Performance tests (k6)

A single k6 script, `k6/payments-load.js`, posts valid synthetic payments to
`POST /api/v1/payments` with a unique `Idempotency-Key` per request.

## Running

```bash
docker compose up -d --build                    # start the demo stack
scripts/wait-for-health.sh http://localhost:8080 180
mkdir -p perf/results
k6 run perf/k6/payments-load.js                 # from the repository root
```

Defaults: ramp from 0 to 20 VUs over 30 s, hold 20 VUs for 60 s, ramp down over 10 s,
100 ms think time between requests. Override with environment variables:

| Variable       | Default                 | Meaning                                  |
|----------------|-------------------------|------------------------------------------|
| `BASE_URL`     | `http://localhost:8080` | API base URL                             |
| `VUS`          | `20`                    | Peak virtual users                       |
| `DURATION`     | `60s`                   | Hold time at peak                        |
| `RAMP_UP`      | `30s`                   | Ramp-up duration                         |
| `RAMP_DOWN`    | `10s`                   | Ramp-down duration                       |
| `THINK_TIME`   | `0.1`                   | Seconds to sleep between iterations      |
| `SUMMARY_PATH` | `perf/results/summary.json` | Where the JSON summary is written    |
| `RUN_ID`       | `k6`                    | Prefix for idempotency keys              |

Example: `VUS=50 DURATION=120s k6 run perf/k6/payments-load.js`.

Thresholds (the run exits non-zero if any fails):

* `http_req_failed` rate below 1 %
* `http_req_duration` p(95) below 500 ms
* `payment_create_duration` (custom trend for the create call) p(95) below 500 ms
* `checks` rate above 99 %

`handleSummary` prints a compact text summary and writes the full k6 summary to
`perf/results/summary.json`. The `perf/results/` directory is git-ignored except for a
committed `sample-*.json`, which is the output of a documented run (see
[`docs/BENCHMARKS.md`](../docs/BENCHMARKS.md)).

Note: the demo profile rate-limits `POST /api/v1/payments` to 200 requests per minute
per client IP (docs/DESIGN.md 4.9). For load runs start the backend with
`PAYMENTS_RATELIMIT_ENABLED=false` (property `payments.ratelimit.enabled`) or
expect 429 responses to count against `http_req_failed`.

## Honesty caveats

* This is a **local synthetic benchmark**: a single laptop runs k6, the JVM, PostgreSQL,
  Kafka, Redis, Prometheus and Grafana inside one Docker VM, all competing for the same
  CPU and disk. The numbers describe that laptop on that day, not the service's capacity.
* It measures the synchronous create path only (request accepted and persisted). The
  asynchronous outbox -> Kafka -> consumer -> settlement path is observed separately through
  the `payment_processing_duration_seconds` metric in Grafana.
* The settlement gateway is a simulator with no network latency, so end-to-end timings
  are optimistic compared with any real payment rail.
* No warm-up run, no JIT stabilisation phase, no repeated runs with variance reporting
  unless `docs/BENCHMARKS.md` says otherwise for a specific recorded run.
* Memory limits in `docker-compose.yml` are sized for a 6 GB VM, which throttles the JVM
  and Kafka compared with a production deployment.

Treat the results as a regression signal ("did the create path get slower?"), not as a
throughput claim.
