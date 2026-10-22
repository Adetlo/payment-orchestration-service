import { render, screen, waitFor, within } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { ROUTER_FUTURE } from "../router";
import { describe, expect, it } from "vitest";
import { Timeline, sortEventsOldestFirst } from "../components/Timeline";
import { PaymentDetailPage } from "../pages/PaymentDetailPage";
import { EVENTS, EVENTS_RESPONSE, PAYMENT, jsonResponse, mockFetch } from "./fixtures";

describe("payment timeline", () => {
  it("orders events oldest first even when the input is shuffled", () => {
    const shuffled = [EVENTS[3], EVENTS[1], EVENTS[0], EVENTS[2]].filter(
      (e): e is NonNullable<typeof e> => e !== undefined,
    );
    const ordered = sortEventsOldestFirst(shuffled);
    expect(ordered.map((e) => e.eventId)).toEqual(["e1", "e2", "e3", "e4"]);
    expect(shuffled.map((e) => e.eventId)).toEqual(["e4", "e2", "e1", "e3"]); // input untouched
  });

  it("renders each event with type, from -> to transition, time and details", () => {
    render(<Timeline events={[...EVENTS].reverse()} />);
    const items = screen.getAllByTestId("timeline-event");
    expect(items).toHaveLength(4);
    expect(items[0]).toHaveTextContent("PaymentReceived");
    expect(items[0]).toHaveTextContent("∅ → RECEIVED");
    expect(items[0]).toHaveTextContent("2026-10-22 10:15:30 UTC");
    expect(items[2]).toHaveTextContent("PROCESSING → RETRY_PENDING");
    expect(items[2]).toHaveTextContent('"retryCount":1');
    expect(items[3]).toHaveTextContent("PaymentCompleted");
    expect(items[3]?.querySelector(".timeline-dot")).toHaveClass("tone-green");
  });

  it("payment detail page shows all PaymentResponse fields, retries, timeline and audit", async () => {
    mockFetch({
      [`/payments/${PAYMENT.paymentId}/events`]: () => jsonResponse(EVENTS_RESPONSE),
      [`/payments/${PAYMENT.paymentId}`]: () => jsonResponse(PAYMENT),
    });
    render(
      <MemoryRouter future={ROUTER_FUTURE} initialEntries={[`/payments/${PAYMENT.paymentId}`]}>
        <Routes>
          <Route path="/payments/:id" element={<PaymentDetailPage />} />
        </Routes>
      </MemoryRouter>,
    );

    await waitFor(() =>
      expect(screen.getByTestId("payment-detail")).toHaveAttribute("data-loaded", "true"),
    );
    const detail = screen.getByTestId("payment-detail");
    expect(detail).toHaveTextContent(PAYMENT.paymentId);
    expect(detail).toHaveTextContent("GB12PAYLAB00000001");
    expect(detail).toHaveTextContent("GB12PAYLAB00000002");
    expect(detail).toHaveTextContent("GBP 125.50");
    expect(detail).toHaveTextContent("INV-DEMO-0003");
    expect(detail).toHaveTextContent("demo-seed-003");
    expect(detail).toHaveTextContent(PAYMENT.correlationId);
    expect(detail).toHaveTextContent("settlement-timeout");

    const retries = screen.getByTestId("payment-retries");
    expect(within(retries).getByTestId("retry-count")).toHaveTextContent("2 / 3");

    await waitFor(() => expect(screen.getAllByTestId("timeline-event")).toHaveLength(4));
    expect(screen.getByTestId("payment-timeline")).toHaveAttribute("data-count", "4");
    const audit = screen.getByTestId("payment-audit");
    expect(within(audit).getAllByTestId("audit-row")).toHaveLength(2);
    expect(audit).toHaveTextContent("PAYMENT_CREATED");
    expect(audit).toHaveTextContent("consumer");
    // COMPLETED is terminal, so no cancel button.
    expect(screen.queryByTestId("cancel-payment")).not.toBeInTheDocument();
  });
});
