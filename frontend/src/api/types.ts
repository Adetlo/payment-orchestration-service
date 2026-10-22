/**
 * Wire types for the Payment Orchestration Lab API.
 * These mirror docs/DESIGN.md section 4.7 exactly; do not add fields the backend does not return.
 * Enum-like fields are typed as `string` where the UI must still render unknown values gracefully;
 * the known values are exported as const arrays for selects and tests.
 */

export const PAYMENT_STATUSES = [
  "RECEIVED",
  "VALIDATED",
  "PROCESSING",
  "COMPLETED",
  "REJECTED",
  "FAILED",
  "RETRY_PENDING",
  "CANCELLED",
] as const;
export type PaymentStatus = (typeof PAYMENT_STATUSES)[number];

export const CURRENCIES = ["GBP", "EUR", "USD", "SGD", "JPY"] as const;
export type Currency = (typeof CURRENCIES)[number];

export const DEMO_FAILURE_MODES = [
  "settlement-timeout",
  "settlement-rejected",
  "poison-message",
] as const;
export type DemoFailureMode = (typeof DEMO_FAILURE_MODES)[number];

export interface CreatePaymentRequest {
  payerAccount: string;
  payeeAccount: string;
  amount: string;
  currency: Currency;
  reference: string;
}

export interface PaymentResponse {
  paymentId: string;
  status: PaymentStatus;
  payerAccount: string;
  payeeAccount: string;
  amount: string;
  currency: string;
  reference: string;
  failureReason: string | null;
  retryCount: number;
  maxRetries: number;
  nextRetryAt: string | null;
  idempotencyKey: string | null;
  correlationId: string;
  demoFailureMode: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type JsonDetails = Record<string, unknown> | null;

export interface PaymentEventResponse {
  eventId: string;
  eventType: string;
  eventVersion: number;
  fromStatus: PaymentStatus | null;
  toStatus: PaymentStatus | null;
  occurredAt: string;
  details: JsonDetails;
}

export interface AuditEntryResponse {
  id: string | number;
  action: string;
  actor: string;
  details: JsonDetails;
  occurredAt: string;
}

export interface PaymentEventsResponse {
  paymentId: string;
  events: PaymentEventResponse[];
  audit: AuditEntryResponse[];
}

export const CLASSIFICATIONS = [
  "MATCHED",
  "AMOUNT_MISMATCH",
  "MISSING_INTERNAL",
  "MISSING_EXTERNAL",
  "STATUS_MISMATCH",
] as const;
export type Classification = (typeof CLASSIFICATIONS)[number];

export const RECONCILIATION_SCENARIOS = ["clean", "with-discrepancies"] as const;
export type ReconciliationScenario = (typeof RECONCILIATION_SCENARIOS)[number];

export interface ReconciliationRunResponse {
  runId: string;
  /** "clean" | "with-discrepancies" */
  scenario: string;
  /** RUNNING | COMPLETED | FAILED */
  status: string;
  startedAt: string;
  completedAt: string | null;
  recordsChecked: number;
  matched: number;
  unmatched: number;
  byClassification: Partial<Record<Classification, number>>;
}

export interface ReconciliationItemResponse {
  id: string | number;
  paymentId: string | null;
  externalReference: string | null;
  /** One of CLASSIFICATIONS. */
  classification: string;
  internalAmount: string | null;
  externalAmount: string | null;
  internalStatus: string | null;
  externalStatus: string | null;
  details: JsonDetails | string | null;
}

export interface ReconciliationRunDetailResponse extends ReconciliationRunResponse {
  items: ReconciliationItemResponse[];
}

export interface RunReconciliationRequest {
  scenario: ReconciliationScenario;
  seed?: number;
}

export const COMPONENT_STATUSES = ["UP", "DEGRADED", "DOWN"] as const;
export type ComponentStatus = (typeof COMPONENT_STATUSES)[number];
export type HealthComponentName = "api" | "postgresql" | "kafka" | "redis";

export interface HealthComponent {
  /** UP | DEGRADED | DOWN */
  status: string;
  detail: string | null;
}

export interface SystemHealthResponse {
  /** UP | DEGRADED | DOWN */
  status: string;
  components: Partial<Record<HealthComponentName, HealthComponent>>;
}

export interface SystemStatsResponse {
  payments: {
    total: number;
    byStatus: Partial<Record<PaymentStatus, number>>;
  };
  outbox: {
    pending: number;
    failed: number;
    published: number;
  };
  kafka: {
    publishedTotal: number;
    deadLetterTotal: number;
  };
  reconciliation: {
    lastRun: ReconciliationRunResponse | null;
  };
}
