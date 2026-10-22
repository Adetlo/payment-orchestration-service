import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ROUTER_FUTURE } from "../router";
import { describe, expect, it } from "vitest";
import { ReconciliationPage } from "../pages/ReconciliationPage";
import { STATS, bodyOf, jsonResponse, mockFetch, urlOf } from "./fixtures";

const RUN = STATS.reconciliation.lastRun;
if (!RUN) throw new Error("fixture must have a last run");

describe("ReconciliationPage", () => {
  it("shows the last run summary with classification counts, lists runs and loads items", async () => {
    mockFetch({
      "/reconciliation/runs/run-1": () =>
        jsonResponse({
          ...RUN,
          items: [
            {
              id: 1,
              paymentId: "p1",
              externalReference: "INV-DEMO-0001",
              classification: "MATCHED",
              internalAmount: "100.00",
              externalAmount: "100.00",
              internalStatus: "COMPLETED",
              externalStatus: "SETTLED",
              details: null,
            },
            {
              id: 2,
              paymentId: "p2",
              externalReference: "INV-DEMO-0002",
              classification: "AMOUNT_MISMATCH",
              internalAmount: "1000.00",
              externalAmount: "1001.00",
              internalStatus: "COMPLETED",
              externalStatus: "SETTLED",
              details: { delta: "1.00" },
            },
          ],
        }),
      "/reconciliation/runs": () =>
        jsonResponse({ items: [RUN], page: 0, size: 10, totalElements: 1, totalPages: 1 }),
    });
    render(
      <MemoryRouter future={ROUTER_FUTURE}>
        <ReconciliationPage />
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("reconciliation-summary")).toHaveAttribute("data-loaded", "true"),
    );
    const summary = screen.getByTestId("reconciliation-summary");
    expect(summary).toHaveTextContent("with-discrepancies");
    expect(within(summary).getByTestId("summary-checked")).toHaveTextContent("9");
    expect(within(summary).getByTestId("class-MATCHED")).toHaveTextContent("Matched 5");
    expect(within(summary).getByTestId("class-STATUS_MISMATCH")).toHaveTextContent(
      "Status Mismatch 1",
    );

    expect(
      within(screen.getByTestId("reconciliation-runs")).getAllByTestId("run-row"),
    ).toHaveLength(1);

    await waitFor(() =>
      expect(
        within(screen.getByTestId("reconciliation-items")).getAllByTestId("item-row"),
      ).toHaveLength(2),
    );
    const pills = within(screen.getByTestId("reconciliation-items")).getAllByTestId(
      "classification-pill",
    );
    expect(pills[0]).toHaveTextContent("MATCHED");
    expect(pills[1]).toHaveTextContent("AMOUNT_MISMATCH");
    expect(pills[1]).toHaveClass("tone-red");
    expect(screen.getByTestId("reconciliation-items")).toHaveTextContent("1,001.00");
  });

  it("runs a reconciliation with the selected scenario and refreshes the run list", async () => {
    let runs = 0;
    const fetchMock = mockFetch({
      "/reconciliation/run": (url, init) => {
        if (init?.method === "POST") {
          runs += 1;
          return jsonResponse({ ...RUN, runId: "run-2", scenario: "clean" });
        }
        if (url.includes("/runs/")) return jsonResponse({ ...RUN, items: [] });
        return jsonResponse({
          items: runs > 0 ? [{ ...RUN, runId: "run-2", scenario: "clean" }, RUN] : [RUN],
          page: 0,
          size: 10,
          totalElements: runs > 0 ? 2 : 1,
          totalPages: 1,
        });
      },
    });
    const user = userEvent.setup();
    render(
      <MemoryRouter future={ROUTER_FUTURE}>
        <ReconciliationPage />
      </MemoryRouter>,
    );
    await waitFor(() => expect(screen.getAllByTestId("run-row")).toHaveLength(1));
    await user.selectOptions(screen.getByTestId("scenario-select"), "clean");
    await user.click(screen.getByTestId("run-reconciliation"));
    await waitFor(() => expect(screen.getAllByTestId("run-row")).toHaveLength(2));

    const post = fetchMock.mock.calls.find((c) => c[1]?.method === "POST");
    expect(urlOf(post?.[0])).toBe("/api/v1/reconciliation/run");
    expect(JSON.parse(bodyOf(post?.[1]))).toEqual({ scenario: "clean", seed: 42 });
  });
});
