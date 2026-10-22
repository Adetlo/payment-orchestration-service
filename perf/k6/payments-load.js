// k6 load script: creates valid synthetic payments with unique idempotency keys.
//
//   k6 run perf/k6/payments-load.js
//   VUS=50 DURATION=120s BASE_URL=http://localhost:8080 k6 run perf/k6/payments-load.js
//
// Scenario: ramping-vus 0 -> VUS over RAMP_UP (30s), hold for DURATION (60s),
// ramp down over RAMP_DOWN (10s). Thresholds: http_req_failed < 1%, p(95) < 500 ms.
// A JSON summary is written to perf/results/summary.json (override with SUMMARY_PATH).
// This is a local synthetic benchmark, not a capacity test: see perf/README.md.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const VUS = Number(__ENV.VUS || 20);
const DURATION = __ENV.DURATION || '60s';
const RAMP_UP = __ENV.RAMP_UP || '30s';
const RAMP_DOWN = __ENV.RAMP_DOWN || '10s';
const THINK_TIME = Number(__ENV.THINK_TIME || 0.1);
const SUMMARY_PATH = __ENV.SUMMARY_PATH || 'perf/results/summary.json';

// Unique per VU (init context runs once per VU) and combined with __ITER below.
const RUN_ID = `${__ENV.RUN_ID || 'k6'}-${Date.now().toString(36)}`;

export const createLatency = new Trend('payment_create_duration', true);
export const created = new Counter('payments_created');
export const replayed = new Counter('payments_replayed');
export const rejected = new Counter('payments_rejected_4xx');

export const options = {
  scenarios: {
    create_payments: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: RAMP_UP, target: VUS },
        { duration: DURATION, target: VUS },
        { duration: RAMP_DOWN, target: 0 },
      ],
      gracefulRampDown: '5s',
      gracefulStop: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500'],
    payment_create_duration: ['p(95)<500'],
    checks: ['rate>0.99'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  userAgent: 'payment-orchestration-service-k6/1.0',
};

const CURRENCIES = ['GBP', 'EUR', 'USD', 'SGD'];
const ACCOUNT_PREFIX = { GBP: 'GB12', EUR: 'DE89', USD: 'US10', SGD: 'SG56' };

function account(prefix, n) {
  return `${prefix}PAYLAB${String(n).padStart(8, '0')}`;
}

function amount(vu, iter) {
  // Deterministic, always two decimals, within the 1,000,000.00 demo limit.
  const pence = ((vu * 7919 + iter * 104729) % 9_999_900) + 100; // 1.00 .. 100,000.00
  return (pence / 100).toFixed(2);
}

export function setup() {
  const res = http.get(`${BASE_URL}/api/v1/system/health`, { timeout: '5s' });
  if (res.status !== 200) {
    throw new Error(`API not reachable at ${BASE_URL} (status ${res.status}); start the stack first`);
  }
  let status = 'unknown';
  try { status = res.json('status'); } catch (e) { /* ignore */ }
  console.log(`target ${BASE_URL}, system health: ${status}, VUs ${VUS}, hold ${DURATION}`);
  return { startedAt: new Date().toISOString() };
}

export default function () {
  const currency = CURRENCIES[(__VU + __ITER) % CURRENCIES.length];
  const prefix = ACCOUNT_PREFIX[currency];
  const key = `${RUN_ID}-${__VU}-${__ITER}`;
  const body = JSON.stringify({
    payerAccount: account(prefix, (__VU % 500) + 1),
    payeeAccount: account(prefix, (__VU % 500) + 1001),
    amount: amount(__VU, __ITER),
    currency,
    reference: `K6-${__VU}-${__ITER}`.slice(0, 35),
  });

  const res = http.post(`${BASE_URL}/api/v1/payments`, body, {
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
      'Idempotency-Key': key,
      'X-Correlation-Id': key,
    },
    tags: { name: 'POST /api/v1/payments' },
    timeout: '10s',
  });

  createLatency.add(res.timings.duration);
  const ok = check(res, {
    'status is 201': (r) => r.status === 201,
    'has paymentId': (r) => {
      try { return typeof r.json('paymentId') === 'string'; } catch (e) { return false; }
    },
    'status is RECEIVED': (r) => {
      try { return r.json('status') === 'RECEIVED'; } catch (e) { return false; }
    },
    'has X-Request-Id': (r) => !!r.headers['X-Request-Id'],
  });

  if (res.status === 201) {
    created.add(1);
    if (String(res.headers['Idempotent-Replayed']).toLowerCase() === 'true') replayed.add(1);
  } else if (res.status >= 400 && res.status < 500) {
    rejected.add(1);
  }
  if (!ok && __ITER < 3) {
    console.warn(`unexpected response ${res.status}: ${String(res.body).slice(0, 200)}`);
  }

  if (THINK_TIME > 0) sleep(THINK_TIME);
}

function fmt(n, digits = 2) {
  return typeof n === 'number' && Number.isFinite(n) ? n.toFixed(digits) : '-';
}

function metricValues(data, name) {
  const m = data.metrics[name];
  return m && m.values ? m.values : {};
}

export function handleSummary(data) {
  const dur = metricValues(data, 'http_req_duration');
  const createDur = metricValues(data, 'payment_create_duration');
  const failed = metricValues(data, 'http_req_failed');
  const reqs = metricValues(data, 'http_reqs');
  const iters = metricValues(data, 'iterations');
  const checks = metricValues(data, 'checks');
  const thresholdResults = Object.entries(data.metrics)
    .filter(([, m]) => m.thresholds)
    .flatMap(([name, m]) => Object.entries(m.thresholds).map(([expr, t]) => `${t.ok ? 'PASS' : 'FAIL'}  ${name}: ${expr}`));

  const lines = [
    '',
    '================ payments-load summary ================',
    `target             ${BASE_URL}`,
    `scenario           ramping-vus 0 -> ${VUS} (${RAMP_UP}), hold ${DURATION}, down ${RAMP_DOWN}`,
    `iterations         ${iters.count || 0}  (${fmt(iters.rate)} /s)`,
    `http requests      ${reqs.count || 0}  (${fmt(reqs.rate)} /s)`,
    `http_req_failed    ${fmt((failed.rate || 0) * 100, 3)} %`,
    `checks passed      ${fmt((checks.rate || 0) * 100, 2)} %`,
    `http_req_duration  avg ${fmt(dur.avg)}  med ${fmt(dur.med)}  p90 ${fmt(dur['p(90)'])}  p95 ${fmt(dur['p(95)'])}  p99 ${fmt(dur['p(99)'])}  max ${fmt(dur.max)}  (ms)`,
    `create latency     avg ${fmt(createDur.avg)}  med ${fmt(createDur.med)}  p95 ${fmt(createDur['p(95)'])}  p99 ${fmt(createDur['p(99)'])}  (ms)`,
    `payments created   ${metricValues(data, 'payments_created').count || 0}`,
    `replayed           ${metricValues(data, 'payments_replayed').count || 0}`,
    `4xx responses      ${metricValues(data, 'payments_rejected_4xx').count || 0}`,
    'thresholds',
    ...thresholdResults.map((l) => `  ${l}`),
    `summary json       ${SUMMARY_PATH}`,
    '=======================================================',
    '',
  ];

  const out = {};
  out[SUMMARY_PATH] = JSON.stringify(
    {
      generatedAt: new Date().toISOString(),
      target: BASE_URL,
      scenario: { executor: 'ramping-vus', vus: VUS, rampUp: RAMP_UP, hold: DURATION, rampDown: RAMP_DOWN, thinkTime: THINK_TIME },
      caveat: 'Local synthetic benchmark against the docker compose demo stack; not a capacity test.',
      ...data,
    },
    null,
    2,
  );
  out.stdout = lines.join('\n');
  return out;
}
