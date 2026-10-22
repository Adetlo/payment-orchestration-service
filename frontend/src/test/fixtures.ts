import { vi } from "vitest";
import type {
  PaymentEventResponse,
  PaymentEventsResponse,
  PaymentResponse,
  SystemHealthResponse,
  SystemStatsResponse,
} from "../api/types";

export const PAYMENT: PaymentResponse = {
  paymentId: "4f0b2a7e-6c3d-4e1f-9a2b-1c2d3e4f5a6b",
  status: "COMPLETED",
  payerAccount: "GB12PAYLAB00000001",
  payeeAccount: "GB12PAYLAB00000002",
  amount: "125.50",
  currency: "GBP",
  reference: "INV-DEMO-0003",
  failureReason: null,
  retryCount: 2,
  maxRetries: 3,
  nextRetryAt: null,
  idempotencyKey: "demo-seed-003",
  correlationId: "a1b2c3d4-0000-4000-8000-000000000001",
  demoFailureMode: "settlement-timeout",
  createdAt: "2026-10-22T10:15:30.123Z",
  updatedAt: "2026-10-22T10:15:36.456Z",
};

export const EVENTS: PaymentEventResponse[] = [
  {
    eventId: "e1",
    eventType: "PaymentReceived",
    eventVersion: 1,
    fromStatus: null,
    toStatus: "RECEIVED",
    occurredAt: "2026-10-22T10:15:30.123Z",
    details: { amount: "125.50", currency: "GBP" },
  },
  {
    eventId: "e2",
    eventType: "PaymentValidated",
    eventVersion: 1,
    fromStatus: "RECEIVED",
    toStatus: "VALIDATED",
    occurredAt: "2026-10-22T10:15:31.000Z",
    details: null,
  },
  {
    eventId: "e3",
    eventType: "PaymentRetryScheduled",
    eventVersion: 1,
    fromStatus: "PROCESSING",
    toStatus: "RETRY_PENDING",
    occurredAt: "2026-10-22T10:15:32.000Z",
    details: { retryCount: 1, nextRetryAt: "2026-10-22T10:15:34.000Z" },
  },
  {
    eventId: "e4",
    eventType: "PaymentCompleted",
    eventVersion: 1,
    fromStatus: "PROCESSING",
    toStatus: "COMPLETED",
    occurredAt: "2026-10-22T10:15:36.456Z",
    details: { fromStatus: "PROCESSING", retryCount: 2 },
  },
];

export const EVENTS_RESPONSE: PaymentEventsResponse = {
  paymentId: PAYMENT.paymentId,
  events: EVENTS,
  audit: [
    {
      id: 1,
      action: "PAYMENT_CREATED",
      actor: "api",
      details: { reference: "INV-DEMO-0003" },
      occurredAt: "2026-10-22T10:15:30.123Z",
    },
    {
      id: 2,
      action: "STATUS_CHANGED",
      actor: "consumer",
      details: { from: "RECEIVED", to: "VALIDATED" },
      occurredAt: "2026-10-22T10:15:31.000Z",
    },
  ],
};

export const STATS: SystemStatsResponse = {
  payments: {
    total: 12,
    byStatus: {
      RECEIVED: 1,
      VALIDATED: 0,
      PROCESSING: 1,
      COMPLETED: 7,
      REJECTED: 1,
      FAILED: 1,
      RETRY_PENDING: 0,
      CANCELLED: 1,
    },
  },
  outbox: { pending: 2, failed: 0, published: 40 },
  kafka: { publishedTotal: 40, deadLetterTotal: 1 },
  reconciliation: {
    lastRun: {
      runId: "run-1",
      scenario: "with-discrepancies",
      status: "COMPLETED",
      startedAt: "2026-10-22T10:20:00.000Z",
      completedAt: "2026-10-22T10:20:00.250Z",
      recordsChecked: 9,
      matched: 5,
      unmatched: 4,
      byClassification: {
        MATCHED: 5,
        AMOUNT_MISMATCH: 1,
        MISSING_INTERNAL: 1,
        MISSING_EXTERNAL: 1,
        STATUS_MISMATCH: 1,
      },
    },
  },
};

export const HEALTH: SystemHealthResponse = {
  status: "DEGRADED",
  components: {
    api: { status: "UP", detail: "v1.0.0" },
    postgresql: { status: "UP", detail: "PostgreSQL 16.4" },
    kafka: { status: "UP", detail: "cluster id abc, 1 node" },
    redis: { status: "DEGRADED", detail: "connection refused; idempotency falls back to DB" },
  },
};

export function jsonResponse(body: unknown, init: ResponseInit = {}): Response {
  const headers = new Headers(init.headers);
  if (!headers.has("content-type")) headers.set("content-type", "application/json");
  return new Response(JSON.stringify(body), { status: 200, ...init, headers });
}

export function problemResponse(
  status: number,
  problem: { title: string; detail?: string; type?: string; [k: string]: unknown },
): Response {
  return new Response(JSON.stringify({ status, ...problem }), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

/** Routes fetch calls by URL substring. Unmatched calls reject loudly. */
export function mockFetch(routes: Record<string, (url: string, init?: RequestInit) => Response>) {
  const fn = vi.fn((input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.href : input.url;
    for (const [needle, handler] of Object.entries(routes)) {
      if (url.includes(needle)) return Promise.resolve(handler(url, init));
    }
    return Promise.reject(new Error(`Unmocked fetch: ${url}`));
  });
  vi.stubGlobal("fetch", fn);
  return fn;
}

export function urlOf(input: RequestInfo | URL | undefined): string {
  if (input === undefined) return "";
  if (typeof input === "string") return input;
  if (input instanceof URL) return input.href;
  return input.url;
}

export function bodyOf(init: RequestInit | undefined): string {
  return typeof init?.body === "string" ? init.body : "";
}
