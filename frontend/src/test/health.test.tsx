import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ROUTER_FUTURE } from "../router";
import { describe, expect, it } from "vitest";
import { HealthPage } from "../pages/HealthPage";
import { HEALTH, jsonResponse, mockFetch } from "./fixtures";

describe("HealthPage", () => {
  it("renders four component cards with UP/DEGRADED/DOWN pills and detail text", async () => {
    mockFetch({
      "/system/health": () =>
        jsonResponse({
          ...HEALTH,
          status: "DOWN",
          components: {
            ...HEALTH.components,
            kafka: { status: "DOWN", detail: "timeout after 2s" },
          },
        }),
    });
    render(
      <MemoryRouter future={ROUTER_FUTURE}>
        <HealthPage />
      </MemoryRouter>,
    );
    await waitFor(() =>
      expect(screen.getByTestId("system-health")).toHaveAttribute("data-loaded", "true"),
    );
    for (const k of ["api", "postgresql", "kafka", "redis"]) {
      expect(screen.getByTestId(`health-${k}`)).toBeInTheDocument();
    }

    const api = within(screen.getByTestId("health-api")).getByTestId("health-pill");
    expect(api).toHaveTextContent("UP");
    expect(api).toHaveClass("tone-green");
    expect(screen.getByTestId("health-postgresql-detail")).toHaveTextContent("PostgreSQL 16.4");

    const kafka = within(screen.getByTestId("health-kafka")).getByTestId("health-pill");
    expect(kafka).toHaveTextContent("DOWN");
    expect(kafka).toHaveClass("tone-red");
    expect(screen.getByTestId("health-kafka-detail")).toHaveTextContent("timeout after 2s");

    const redis = within(screen.getByTestId("health-redis")).getByTestId("health-pill");
    expect(redis).toHaveTextContent("DEGRADED");
    expect(redis).toHaveClass("tone-amber");
  });

  it("toggles the raw JSON view", async () => {
    mockFetch({ "/system/health": () => jsonResponse(HEALTH) });
    const user = userEvent.setup();
    render(
      <MemoryRouter future={ROUTER_FUTURE}>
        <HealthPage />
      </MemoryRouter>,
    );
    const toggle = await screen.findByTestId("json-toggle");
    expect(screen.queryByTestId("json-raw")).not.toBeInTheDocument();
    await user.click(toggle);
    expect(screen.getByTestId("json-raw")).toHaveTextContent('"status": "DEGRADED"');
    expect(toggle).toHaveAttribute("aria-expanded", "true");
  });
});
