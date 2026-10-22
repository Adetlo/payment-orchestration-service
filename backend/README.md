# Payment Orchestration Service — backend

Java 21 / Spring Boot 3.5 service implementing the contract in [`../docs/DESIGN.md`](../docs/DESIGN.md):
idempotent payment intake, an explicit state machine, a transactional outbox to Kafka, a Kafka
consumer with bounded retries and a dead-letter topic, a synthetic settlement gateway, reconciliation,
an append-only audit trail, Prometheus metrics and structured logs. Everything is synthetic.

## Run locally

Requires JDK 21, Maven 3.9 and Docker. Start the infrastructure (or use the repository
`docker-compose.yml`):

```bash
docker run -d --name pos-dev-postgres -p 5432:5432 -e POSTGRES_DB=payments -e POSTGRES_USER=payments -e POSTGRES_PASSWORD=payments_local postgres:16-alpine
docker run -d --name pos-dev-kafka -p 9092:9092 -e KAFKA_NODE_ID=1 -e KAFKA_PROCESS_ROLES=broker,controller \
  -e KAFKA_LISTENERS=PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 -e KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER \
  -e KAFKA_CONTROLLER_QUORUM_VOTERS=1@localhost:9093 -e KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT \
  -e KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1 -e KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR=1 \
  -e KAFKA_TRANSACTION_STATE_LOG_MIN_ISR=1 apache/kafka:3.9.1
docker run -d --name pos-dev-redis -p 6379:6379 redis:7-alpine

mvn spring-boot:run -Dspring-boot.run.profiles=local,demo
```

Swagger UI: <http://localhost:8080/swagger-ui.html>; OpenAPI: `/v3/api-docs`; actuator:
`/actuator/health`, `/actuator/prometheus`. Sample requests and real responses are in
[`docs/API_EXAMPLES.md`](../docs/API_EXAMPLES.md).

## Profiles

| Profile  | Purpose |
|----------|---------|
| `local`  | default; localhost infrastructure, plain-text logs |
| `docker` | compose host names (`postgres`, `kafka:29092`, `redis`), JSON logs |
| `demo`   | enables the `X-Demo-Failure` header (`settlement-timeout`, `settlement-rejected`, `poison-message`) |
| `test`   | used by the test suite: rate limiting off, faster outbox polling |
| `prod`   | failure injection hard-disabled, Swagger UI off, JSON logs |

Without `demo`, `X-Demo-Failure` is ignored (and the fact is recorded in the audit entry).

## Configuration

Standard Spring properties (`spring.datasource.*`, `spring.kafka.bootstrap-servers`,
`spring.data.redis.*`) plus the `payments.*` block in `application.yml`:

| Property | Default | Meaning |
|----------|---------|---------|
| `payments.failure-injection.enabled` | `false` (`true` in `demo`) | honour `X-Demo-Failure` |
| `payments.retry.max-attempts` | `3` | settlement retries before FAILED |
| `payments.retry.poll-interval` / `batch-size` | `1s` / `20` | retry scheduler cadence |
| `payments.outbox.poll-interval` / `batch-size` / `max-attempts` | `500ms` / `50` / `5` | outbox publisher cadence and send retries (1s,2s,4s,8s,16s backoff) |
| `payments.ratelimit.enabled` / `limit` / `window` | `true` / `200` / `1m` | fixed-window limit on `POST /api/v1/payments` per client IP |
| `payments.idempotency.ttl` / `lock-ttl` / `cleanup-cron` | `24h` / `10s` / hourly | idempotency record lifetime, Redis lock, cleanup |
| `payments.cors.allowed-origins` | `localhost:3000`, `localhost:5173` | browser origins |
| `payments.max-request-bytes` | `65536` | request body limit (413 above) |

Any property can be overridden with environment variables, e.g.
`SPRING_DATASOURCE_URL`, `SPRING_KAFKA_BOOTSTRAP_SERVERS`, `SPRING_DATA_REDIS_HOST`,
`PAYMENTS_FAILURE_INJECTION_ENABLED`, `SPRING_PROFILES_ACTIVE`.

## Tests

```bash
mvn verify            # unit (*Test, Surefire) + integration (*IT, Failsafe) + Spotless + SpotBugs + JaCoCo
mvn test              # unit tests only
mvn spotless:apply    # format (google-java-format, AOSP style)
```

Integration tests start PostgreSQL, Kafka and Redis once per JVM with Testcontainers
(`IntegrationTestSupport`), so Docker must be reachable. The JaCoCo report is written to
`target/site/jacoco/index.html`.

## Docker image

```bash
docker build -t payment-orchestration-backend:local .
```

Multi-stage build (dependency layer cached separately), `eclipse-temurin:21-jre-alpine` runtime,
non-root user `app` (uid 10001), container-aware heap (`-XX:MaxRAMPercentage=70`) and a
`HEALTHCHECK` on `/actuator/health/readiness`.

## Behaviour notes

* The processing step `VALIDATED -> PROCESSING` is recorded as the internal history event
  `PaymentProcessingStarted`; it is not published to Kafka.
* A record that is dead-lettered (3 delivery attempts, 500 ms apart) is counted, audited
  (`payment.dead-lettered`) and the payment is parked as `REJECTED` with an explicit
  `failureReason`, because `FAILED` is not reachable from `RECEIVED` in the state table.
* Security is demo level: no authentication. An OAuth2 resource server would be configured in
  `api/web` (a `SecurityFilterChain` bean) and the controllers annotated with method security.
* Metric names follow DESIGN.md with two forced exceptions: the Prometheus client reserves the
  `_created` and `_total` suffixes (they are stripped from the metric name), so the creation
  counter is exposed as `payment_received_total` (not `payment_created_total`) and the outbox
  gauges as `outbox_pending` / `outbox_failed` (not `outbox_*_total`).
