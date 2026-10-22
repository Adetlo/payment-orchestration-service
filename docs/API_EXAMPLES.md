# API examples

All responses in this file are real output of the application; nothing is hand-written.
Field names, headers and status codes are therefore exactly what the running service returns.

Captured with `curl` against the service running with the `local,demo` profiles on 2026-10-22,
with PostgreSQL, Kafka (KRaft) and Redis in Docker; the Docker Compose stack behaves identically.
Responses are verbatim; a few repeated security/CORS headers were trimmed.

## 1. Create a payment (with `Idempotency-Key`)

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: order-1792685594' -H 'X-Correlation-Id: demo-corr-001' \
  -d '{"payerAccount":"GB12PAYLAB00000001","payeeAccount":"GB12PAYLAB00000002","amount":"125.50","currency":"GBP","reference":"INV-1001"}'
```

```http
HTTP/1.1 201
X-Request-Id: 16c1d01e-5cc4-48f1-83c1-5c350c26589a
X-Correlation-Id: demo-corr-001
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Cache-Control: no-store
X-RateLimit-Remaining: 199
Location: /api/v1/payments/b47835dd-6c6c-4e76-8e2a-dbccbe847186
Content-Type: application/json

{"paymentId":"b47835dd-6c6c-4e76-8e2a-dbccbe847186","status":"RECEIVED","payerAccount":"GB12PAYLAB00000001","payeeAccount":"GB12PAYLAB00000002","amount":"125.50","currency":"GBP","reference":"INV-1001","failureReason":null,"retryCount":0,"maxRetries":3,"nextRetryAt":null,"idempotencyKey":"order-1792685594","correlationId":"demo-corr-001","demoFailureMode":null,"createdAt":"2026-10-22T16:13:14.238349Z","updatedAt":"2026-10-22T16:13:14.238349Z"}
```

## 2. Replay with the same key and body

```http
HTTP/1.1 201
Location: /api/v1/payments/b47835dd-6c6c-4e76-8e2a-dbccbe847186
Idempotent-Replayed: true
Content-Type: application/json
Content-Length: 447

{"paymentId":"b47835dd-6c6c-4e76-8e2a-dbccbe847186","status":"RECEIVED", ... identical to the first response (same 447 bytes) ...}
```

## 3. Same key, different payload

```http
HTTP/1.1 409
Content-Type: application/problem+json

{"type":"about:blank#idempotency-key-reuse","title":"Idempotency key reused","status":409,"detail":"Idempotency-Key 'order-1792685594' was already used with a different request payload","instance":"/api/v1/payments"}
```

## 4. Validation error

```bash
curl -i -X POST http://localhost:8080/api/v1/payments -H 'Content-Type: application/json' \
  -d '{"payerAccount":"bad","payeeAccount":"GB12PAYLAB00000002","amount":"10.005","currency":"GBP","reference":""}'
```

```http
HTTP/1.1 400
Content-Type: application/problem+json

{"type":"about:blank#validation-error","title":"Validation failed","status":400,"detail":"Request validation failed","instance":"/api/v1/payments","errors":[{"field":"reference","message":"must be 1..35 characters of [A-Za-z0-9 ./-]"},{"field":"reference","message":"must not be blank"},{"field":"payerAccount","message":"must be a synthetic IBAN-like reference"}]}
```

## 5. The payment a moment later, and its timeline

`GET /api/v1/payments/b47835dd-6c6c-4e76-8e2a-dbccbe847186`

```json
{"paymentId":"b47835dd-6c6c-4e76-8e2a-dbccbe847186","status":"COMPLETED","payerAccount":"GB12PAYLAB00000001","payeeAccount":"GB12PAYLAB00000002","amount":"125.50","currency":"GBP","reference":"INV-1001","failureReason":null,"retryCount":0,"maxRetries":3,"nextRetryAt":null,"idempotencyKey":"order-1792685594","correlationId":"demo-corr-001","demoFailureMode":null,"createdAt":"2026-10-22T16:13:14.238349Z","updatedAt":"2026-10-22T16:13:14.587289Z"}
```

`GET /api/v1/payments/b47835dd-6c6c-4e76-8e2a-dbccbe847186/events` (trimmed to the first event and audit entries)

```json
{"paymentId":"b47835dd-6c6c-4e76-8e2a-dbccbe847186",
 "events":[
  {"eventId":"b0fdd88a-6adb-4bd6-a08d-9a4f133aa907","eventType":"PaymentReceived","eventVersion":1,"fromStatus":null,"toStatus":"RECEIVED","occurredAt":"2026-10-22T16:13:14.238476Z","details":{"amount":"125.50","status":"RECEIVED","currency":"GBP","reference":"INV-1001","fromStatus":null,"retryCount":0,"failureReason":null,"idempotencyKey":"order-1792685594","payeeAccountMasked":"GB**...0002","payerAccountMasked":"GB**...0001"}},
  {"eventType":"PaymentValidated","fromStatus":"RECEIVED","toStatus":"VALIDATED","occurredAt":"2026-10-22T16:13:14.575311Z", "...":"..."},
  {"eventType":"PaymentProcessingStarted","fromStatus":"VALIDATED","toStatus":"PROCESSING","occurredAt":"2026-10-22T16:13:14.579828Z", "...":"..."},
  {"eventType":"PaymentCompleted","fromStatus":"PROCESSING","toStatus":"COMPLETED","occurredAt":"2026-10-22T16:13:14.587289Z", "...":"..."}],
 "audit":[
  {"id":1,"action":"payment.created","actor":"api","details":{"actor":"api","status":"RECEIVED","eventId":"b0fdd88a-6adb-4bd6-a08d-9a4f133aa907","fromStatus":null,"failureReason":null,"idempotencyKey":"order-1792685594"},"occurredAt":"2026-10-22T16:13:14.238476Z"},
  {"id":2,"action":"payment.validated","actor":"consumer","details":{"actor":"consumer","status":"VALIDATED","eventId":"ca0f2faf-4370-4d58-915d-41c9251e1b1c","fromStatus":"RECEIVED","failureReason":null},"occurredAt":"2026-10-22T16:13:14.575311Z"},
  {"id":3,"action":"payment.processing","actor":"consumer", "...":"..."},
  {"id":4,"action":"payment.completed","actor":"consumer", "...":"..."}]}
```

## 6. `X-Demo-Failure: settlement-timeout` (polled every 500 ms)

```
t+1: RECEIVED
t+2: RETRY_PENDING
...
t+8: RETRY_PENDING
t+9: COMPLETED
```

```json
{"paymentId":"c071e9c5-7e7e-4287-bcc7-e322909326c4","status":"COMPLETED","payerAccount":"GB12PAYLAB00000001","payeeAccount":"GB12PAYLAB00000002","amount":"125.50","currency":"GBP","reference":"INV-TIMEOUT","failureReason":null,"retryCount":2,"maxRetries":3,"nextRetryAt":null,"idempotencyKey":null,"correlationId":"d100313d-f3f9-42fd-a1a1-dec768b52725","demoFailureMode":"settlement-timeout","createdAt":"2026-10-22T16:13:17.437279Z","updatedAt":"2026-10-22T16:13:21.564661Z"}
```

## 7. Cancel

A `RETRY_PENDING` payment (`POST /api/v1/payments/{id}/cancel` with `{"reason":"customer changed mind"}`):

```http
HTTP/1.1 200

{"paymentId":"df098085-1ae0-4aa2-8e7a-5edb7c232bc8","status":"CANCELLED","payerAccount":"GB12PAYLAB00000001","payeeAccount":"GB12PAYLAB00000002","amount":"125.50","currency":"GBP","reference":"INV-CANCEL","failureReason":"customer changed mind","retryCount":1,"maxRetries":3,"nextRetryAt":null,"idempotencyKey":null,"correlationId":"44f5bc95-1d92-4350-a3f1-60aaeffe7e94","demoFailureMode":"settlement-timeout","createdAt":"2026-10-22T16:13:21.674469Z","updatedAt":"2026-10-22T16:13:22.273960Z"}
```

The `COMPLETED` payment from step 1:

```http
HTTP/1.1 409
Content-Type: application/problem+json

{"type":"about:blank#invalid-state-transition","title":"Invalid state transition","status":409,"detail":"Payment b47835dd-6c6c-4e76-8e2a-dbccbe847186 cannot move from COMPLETED to CANCELLED","instance":"/api/v1/payments/b47835dd-6c6c-4e76-8e2a-dbccbe847186/cancel","paymentId":"b47835dd-6c6c-4e76-8e2a-dbccbe847186","fromStatus":"COMPLETED","toStatus":"CANCELLED"}
```

## 8. `settlement-rejected` and `poison-message`

```json
{"paymentId":"8c14d735-9dbc-41e0-9245-56f297ab8012","status":"FAILED","reference":"INV-REJECT","failureReason":"settlement network rejected the payment (demo)","retryCount":0,"demoFailureMode":"settlement-rejected", "...":"..."}
{"paymentId":"77fd353d-a884-451f-bac1-73486b71f186","status":"REJECTED","reference":"INV-POISON","failureReason":"dead-lettered after repeated delivery failures: PoisonMessageException","retryCount":0,"demoFailureMode":"poison-message", "...":"..."}
```

The poison record was delivered three times (500 ms apart), published to `payment.events.v1.dlt`,
counted in `payment_dead_letter_total` and audited as `payment.dead-lettered`.

## 9. Reconciliation (`{"scenario":"with-discrepancies","seed":42}`)

```json
{"runId":"3710d9a7-6a2c-4ec5-bd44-8b2723d6fc9c","scenario":"with-discrepancies","status":"COMPLETED","startedAt":"2026-10-22T16:13:28.357847Z","completedAt":"2026-10-22T16:13:28.368157Z","recordsChecked":4,"matched":0,"unmatched":4,
 "byClassification":{"MATCHED":0,"AMOUNT_MISMATCH":1,"MISSING_INTERNAL":1,"MISSING_EXTERNAL":1,"STATUS_MISMATCH":1},
 "items":[
  {"id":1,"paymentId":"b47835dd-6c6c-4e76-8e2a-dbccbe847186","externalReference":null,"classification":"MISSING_EXTERNAL","internalAmount":"125.50","externalAmount":null,"internalStatus":"COMPLETED","externalStatus":null,"details":"payment absent from the settlement feed"},
  {"id":2,"paymentId":"c071e9c5-7e7e-4287-bcc7-e322909326c4","externalReference":"SETTLE-C071E9C5","classification":"AMOUNT_MISMATCH","internalAmount":"125.50","externalAmount":"125.51","internalStatus":"COMPLETED","externalStatus":"SETTLED","details":"internal 125.50 vs external 125.51"},
  {"id":3,"paymentId":"8c14d735-9dbc-41e0-9245-56f297ab8012","externalReference":"SETTLE-8C14D735","classification":"STATUS_MISMATCH","internalAmount":"125.50","externalAmount":"125.50","internalStatus":"FAILED","externalStatus":"SETTLED","details":"internal FAILED vs external SETTLED"},
  {"id":4,"paymentId":"0c45c028-4f08-3ce4-f12b-bb4b46ee9d83","externalReference":"SETTLE-0C45C028","classification":"MISSING_INTERNAL","internalAmount":null,"externalAmount":"99.99","internalStatus":null,"externalStatus":"SETTLED","details":"external record has no internal payment"}]}
```

## 10. `GET /api/v1/system/stats`

```json
{"payments":{"total":5,"byStatus":{"RECEIVED":0,"VALIDATED":0,"PROCESSING":0,"COMPLETED":2,"REJECTED":1,"FAILED":1,"RETRY_PENDING":0,"CANCELLED":1}},"outbox":{"pending":1,"failed":0,"published":17},"kafka":{"publishedTotal":17,"deadLetterTotal":1},"reconciliation":{"lastRun":{"runId":"3710d9a7-6a2c-4ec5-bd44-8b2723d6fc9c","scenario":"with-discrepancies","status":"COMPLETED","startedAt":"2026-10-22T16:13:28.357847Z","completedAt":"2026-10-22T16:13:28.368157Z","recordsChecked":4,"matched":0,"unmatched":4,"byClassification":{"MATCHED":0,"AMOUNT_MISMATCH":1,"MISSING_INTERNAL":1,"MISSING_EXTERNAL":1,"STATUS_MISMATCH":1}}}}
```

## 11. `GET /api/v1/system/health`

```json
{"status":"UP","components":{"api":{"status":"UP","detail":"serving requests"},"postgresql":{"status":"UP","detail":"connection valid"},"kafka":{"status":"UP","detail":"cluster reachable, nodes=1"},"redis":{"status":"UP","detail":"ping ok"}}}
```

## 12. `GET /actuator/prometheus` (business metrics)

```
idempotency_conflict_total{application="payment-orchestration-service"} 1.0
idempotency_replay_total{application="payment-orchestration-service"} 2.0
outbox_failed{application="payment-orchestration-service"} 0.0
outbox_pending{application="payment-orchestration-service"} 1.0
outbox_published_total{application="payment-orchestration-service"} 17.0
payment_cancelled_total{application="payment-orchestration-service"} 1.0
payment_completed_total{application="payment-orchestration-service"} 2.0
payment_dead_letter_total{application="payment-orchestration-service"} 1.0
payment_failed_total{application="payment-orchestration-service"} 1.0
payment_processing_duration_seconds_count{application="payment-orchestration-service"} 5
payment_processing_duration_seconds_sum{application="payment-orchestration-service"} 6.687215
payment_received_total{application="payment-orchestration-service"} 5.0
payment_rejected_total{application="payment-orchestration-service"} 1.0
payment_retry_total{application="payment-orchestration-service"} 3.0
reconciliation_mismatch_total{application="payment-orchestration-service",classification="AMOUNT_MISMATCH"} 1.0
reconciliation_mismatch_total{application="payment-orchestration-service",classification="MISSING_EXTERNAL"} 1.0
reconciliation_mismatch_total{application="payment-orchestration-service",classification="MISSING_INTERNAL"} 1.0
reconciliation_mismatch_total{application="payment-orchestration-service",classification="STATUS_MISMATCH"} 1.0
reconciliation_runs_total{application="payment-orchestration-service"} 1.0
```

`payment_received_total` and `outbox_pending` / `outbox_failed` are the Prometheus-legal names for
the design's `payment_created_total` and `outbox_*_total` (see README, "Behaviour notes").

## 13. `GET /api/v1/payments?status=COMPLETED&currency=GBP&size=2` (page shape)

```json
{
 "items": [
  {
   "paymentId": "9632bbde-411a-48bc-a561-64f09fd593a3",
   "status": "COMPLETED",
   "payerAccount": "GB12PAYLAB00000019",
   "payeeAccount": "GB12PAYLAB00000020",
   "amount": "18250.00",
   "currency": "GBP",
   "reference": "INV-DEMO-0010",
   "failureReason": null,
   "retryCount": 0,
   "maxRetries": 3,
   "nextRetryAt": null,
   "idempotencyKey": "demo-seed-010",
   "correlationId": "c1fa071a-68c8-4b32-bd5f-19c8ef38b140",
   "demoFailureMode": null,
   "createdAt": "2026-10-22T16:22:48.812159Z",
   "updatedAt": "2026-10-22T16:22:49.135583Z"
  }
 ],
 "page": 0,
 "size": 2,
 "totalElements": 4,
 "totalPages": 2
}
```
(`items` trimmed to one element.)

## 14. `GET /api/v1/system/health` with Redis stopped (`docker compose stop redis`)

```json
{"status":"DEGRADED","components":{"api":{"status":"UP","detail":"serving requests"},"postgresql":{"status":"UP","detail":"connection valid"},"kafka":{"status":"UP","detail":"cluster reachable, nodes=1"},"redis":{"status":"DOWN","detail":"RedisConnectionFailureException: Unable to connect to Redis"}}}
```

Redis only backs the idempotency lock and the rate limiter, so the API keeps serving and reports
`DEGRADED` rather than `DOWN` (ADR 0007).

## 15. Kafka event envelope as published on `payment.events.v1`

Read with `kafka-console-consumer.sh --topic payment.events.v1 --from-beginning --max-messages 1`:

```json
{
 "data": {
  "amount": "310.75",
  "status": "RECEIVED",
  "currency": "EUR",
  "reference": "INV-DEMO-0003",
  "fromStatus": null,
  "retryCount": 0,
  "failureReason": null,
  "idempotencyKey": "demo-seed-003",
  "demoFailureMode": "settlement-timeout",
  "payeeAccountMasked": "DE**...0006",
  "payerAccountMasked": "DE**...0005"
 },
 "eventId": "194eb180-074f-48eb-af27-188916dbb1e5",
 "eventType": "PaymentReceived",
 "paymentId": "05e605f3-9641-4408-a839-6cc00b8074e3",
 "occurredAt": "2026-10-22T16:22:48.455613554Z",
 "eventVersion": 1,
 "correlationId": "ab30bf5f-e896-443d-abcc-e73753c99f6f"
}
```

Account numbers are masked in events; the key of the record is the `paymentId`, so all events of one
payment stay in order on one partition.

## 16. Structured log line (profile `docker`, JSON via logstash-logback-encoder)

```json
{"@timestamp":"2026-10-22T16:22:48.334432719Z","@version":"1","message":"Payment 5b5aecb6-0577-44c2-9e8f-214dc248d37b received (125.50 GBP)","logger_name":"io.github.adetlo.payments.application.PaymentService","thread_name":"http-nio-8080-exec-7","level":"INFO","level_value":20000,"requestId":"c9a8b723-ea8d-4dcb-93d9-6d5553ba7753","correlationId":"5be837dc-aee7-4009-a901-8494c5264117","service":"payment-orchestration-service"}
```
