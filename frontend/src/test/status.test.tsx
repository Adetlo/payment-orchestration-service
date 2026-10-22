import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { PAYMENT_STATUSES } from "../api/types";
import { ClassificationPill, HealthPill, StatusPill } from "../components/Pills";
import { classificationTone, healthTone, paymentStatusTone } from "../lib/status";

describe("status pill mapping", () => {
  it("maps every payment status to the colour from DESIGN.md section 5", () => {
    expect(paymentStatusTone("COMPLETED")).toBe("green");
    expect(paymentStatusTone("FAILED")).toBe("red");
    expect(paymentStatusTone("REJECTED")).toBe("red");
    expect(paymentStatusTone("PROCESSING")).toBe("amber");
    expect(paymentStatusTone("RETRY_PENDING")).toBe("amber");
    expect(paymentStatusTone("RECEIVED")).toBe("blue");
    expect(paymentStatusTone("VALIDATED")).toBe("blue");
    expect(paymentStatusTone("CANCELLED")).toBe("grey");
    expect(paymentStatusTone("SOMETHING_NEW")).toBe("grey");
  });

  it("renders a pill with the tone class and the raw status text for every status", () => {
    render(
      <>
        {PAYMENT_STATUSES.map((s) => (
          <StatusPill key={s} status={s} />
        ))}
      </>,
    );
    const pills = screen.getAllByTestId("status-pill");
    expect(pills).toHaveLength(PAYMENT_STATUSES.length);
    const completed = screen.getByText("COMPLETED");
    expect(completed).toHaveClass("pill", "tone-green");
    expect(screen.getByText("RETRY_PENDING")).toHaveClass("tone-amber");
    expect(screen.getByText("CANCELLED")).toHaveClass("tone-grey");
  });

  it("maps health states: UP green, DEGRADED amber, DOWN red, unknown grey", () => {
    expect(healthTone("UP")).toBe("green");
    expect(healthTone("DEGRADED")).toBe("amber");
    expect(healthTone("DOWN")).toBe("red");
    expect(healthTone(undefined)).toBe("grey");
    render(<HealthPill status="DOWN" />);
    expect(screen.getByTestId("health-pill")).toHaveClass("tone-red");
    expect(screen.getByTestId("health-pill")).toHaveTextContent("DOWN");
  });

  it("maps reconciliation classifications", () => {
    expect(classificationTone("MATCHED")).toBe("green");
    expect(classificationTone("AMOUNT_MISMATCH")).toBe("red");
    expect(classificationTone("STATUS_MISMATCH")).toBe("red");
    expect(classificationTone("MISSING_INTERNAL")).toBe("amber");
    expect(classificationTone("MISSING_EXTERNAL")).toBe("amber");
    render(<ClassificationPill classification="MISSING_EXTERNAL" />);
    expect(screen.getByTestId("classification-pill")).toHaveClass("tone-amber");
  });
});
