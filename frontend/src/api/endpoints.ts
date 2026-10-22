import { apiGet, apiPost, type ApiResponse, type QueryValue } from "./client";
import type {
  CreatePaymentRequest,
  PageResponse,
  PaymentEventsResponse,
  PaymentResponse,
  ReconciliationRunDetailResponse,
  ReconciliationRunResponse,
  RunReconciliationRequest,
  SystemHealthResponse,
  SystemStatsResponse,
} from "./types";

export interface PaymentsQuery {
  status?: string;
  currency?: string;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export function listPayments(query: PaymentsQuery): Promise<PageResponse<PaymentResponse>> {
  return apiGet<PageResponse<PaymentResponse>>("/payments", query as Record<string, QueryValue>);
}

export function getPayment(id: string): Promise<PaymentResponse> {
  return apiGet<PaymentResponse>(`/payments/${encodeURIComponent(id)}`);
}

export function getPaymentEvents(id: string): Promise<PaymentEventsResponse> {
  return apiGet<PaymentEventsResponse>(`/payments/${encodeURIComponent(id)}/events`);
}

export interface CreatePaymentOptions {
  idempotencyKey?: string;
  demoFailure?: string;
}

export function createPayment(
  body: CreatePaymentRequest,
  options: CreatePaymentOptions = {},
): Promise<ApiResponse<PaymentResponse>> {
  return apiPost<PaymentResponse>("/payments", body, {
    "Idempotency-Key": options.idempotencyKey,
    "X-Demo-Failure": options.demoFailure,
  });
}

export function cancelPayment(id: string, reason?: string): Promise<ApiResponse<PaymentResponse>> {
  return apiPost<PaymentResponse>(
    `/payments/${encodeURIComponent(id)}/cancel`,
    reason ? { reason } : {},
  );
}

export function runReconciliation(
  body: RunReconciliationRequest,
): Promise<ApiResponse<ReconciliationRunResponse>> {
  return apiPost<ReconciliationRunResponse>("/reconciliation/run", body);
}

export function listReconciliationRuns(
  page = 0,
  size = 10,
): Promise<PageResponse<ReconciliationRunResponse>> {
  return apiGet<PageResponse<ReconciliationRunResponse>>("/reconciliation/runs", { page, size });
}

export function getReconciliationRun(runId: string): Promise<ReconciliationRunDetailResponse> {
  return apiGet<ReconciliationRunDetailResponse>(
    `/reconciliation/runs/${encodeURIComponent(runId)}`,
  );
}

export function getSystemHealth(): Promise<SystemHealthResponse> {
  return apiGet<SystemHealthResponse>("/system/health");
}

export function getSystemStats(): Promise<SystemStatsResponse> {
  return apiGet<SystemStatsResponse>("/system/stats");
}
