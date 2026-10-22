import type { PaymentStatus, SystemStatsResponse } from "../api/types";

export interface DashboardAggregates {
  total: number;
  successful: number;
  failed: number;
  pending: number;
  cancelled: number;
}

const SUCCESSFUL: readonly PaymentStatus[] = ["COMPLETED"];
const FAILED: readonly PaymentStatus[] = ["FAILED", "REJECTED"];
const PENDING: readonly PaymentStatus[] = ["RECEIVED", "VALIDATED", "PROCESSING", "RETRY_PENDING"];
const CANCELLED: readonly PaymentStatus[] = ["CANCELLED"];

function sum(byStatus: Partial<Record<PaymentStatus, number>>, keys: readonly PaymentStatus[]) {
  return keys.reduce((acc, key) => acc + (byStatus[key] ?? 0), 0);
}

export function aggregateStats(stats: SystemStatsResponse | null): DashboardAggregates {
  const byStatus = stats?.payments.byStatus ?? {};
  return {
    total: stats?.payments.total ?? 0,
    successful: sum(byStatus, SUCCESSFUL),
    failed: sum(byStatus, FAILED),
    pending: sum(byStatus, PENDING),
    cancelled: sum(byStatus, CANCELLED),
  };
}
