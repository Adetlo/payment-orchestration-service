import { render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { ROUTER_FUTURE } from "../router";
import { describe, expect, it } from "vitest";
import { aggregateStats } from "../lib/stats";
import { DashboardPage } from "../pages/DashboardPage";
import { HEALTH, PAYMENT, STATS, jsonResponse, mockFetch } from "./fixtures";

describe("dashboard aggregates", () => {
  it("computes successful/failed/pending buckets from /system/stats byStatus", () => {
    const agg = aggregateStats(STATS);
    expect(agg.total).toBe(12);
    expect(agg.successful).toBe(7);
    expect(agg.failed).toBe(2); // FAILED + REJECTED
    expect(agg.pending).toBe(2); // RECEIVED + VALIDATED + PROCESSING + RETRY_PENDING
    expect(agg.cancelled).toBe(1);
  });

  it("treats missing statuses as zero and null stats as empty", () => {
    expect(aggregateStats(null)).toEqual({
      total: 0,
      successful: 0,
      failed: 0,
      pending: 0,
      cancelled: 0,
    });
    const agg = aggregateStats({
      ...STATS,
      payments: { total: 3, byStatus: { COMPLETED: 3 } },
    });
    expect(agg).toMatchObject({ total: 3, successful: 3, failed: 0, pending: 0 });
  });

  it("renders stat cards, outbox/kafka numbers, health and recent payments from the API", async () => {
    mockFetch({
      "/system/stats": () => jsonResponse(STATS),
      "/system/health": () => jsonResponse(HEALTH),
      "/payments?": () =>
        jsonResponse({
          items: [
            PAYMENT,
            { ...PAYMENT, paymentId: "second", status: "FAILED", reference: "INV-2" },
          ],
          page: 0,
          size: 8,
          totalElements: 2,
          totalPages: 1,
        }),
    });
    render(
      <MemoryRouter future={ROUTER_FUTURE}>
        <DashboardPage />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("dashboard-stats")).toHaveAttribute("data-loaded", "true"),
    );
    expect(screen.getByTestId("stat-total-value")).toHaveTextContent("12");
    expect(screen.getByTestId("stat-successful-value")).toHaveTextContent("7");
    expect(screen.getByTestId("stat-failed-value")).toHaveTextContent("2");
    expect(screen.getByTestId("stat-pending-value")).toHaveTextContent("2");
    expect(screen.getByTestId("stat-outbox")).toHaveTextContent("40 published");
    expect(screen.getByTestId("stat-kafka-value")).toHaveTextContent("40");
    expect(screen.getByTestId("stat-kafka")).toHaveTextContent("1 dead-lettered");
    expect(screen.getByTestId("stat-reconciliation")).toHaveTextContent("COMPLETED");
    expect(screen.getByTestId("stat-reconciliation")).toHaveTextContent("5 matched · 4 unmatched");
    expect(screen.getByTestId("stat-health")).toHaveTextContent("DEGRADED");
    expect(screen.getByTestId("stat-health")).toHaveTextContent("redis DEGRADED");

    await waitFor(() => expect(screen.getAllByTestId("payment-row")).toHaveLength(2));
    const recent = screen.getByTestId("dashboard-recent");
    expect(recent).toHaveTextContent("INV-DEMO-0003");
    expect(recent).toHaveTextContent("GBP 125.50");
  });
});
