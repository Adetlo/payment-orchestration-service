import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { ROUTER_FUTURE } from "../router";
import { describe, expect, it } from "vitest";
import { CreatePaymentPage } from "../pages/CreatePaymentPage";
import { PAYMENT, bodyOf, jsonResponse, mockFetch, problemResponse } from "./fixtures";

function renderPage() {
  return render(
    <MemoryRouter future={ROUTER_FUTURE}>
      <CreatePaymentPage />
    </MemoryRouter>,
  );
}

async function fillValidForm(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText("Payer account"), "gb12paylab00000001");
  await user.type(screen.getByLabelText("Payee account"), "GB12PAYLAB00000002");
  await user.type(screen.getByLabelText("Amount"), "125.50");
  await user.selectOptions(screen.getByLabelText("Currency"), "GBP");
  await user.type(screen.getByLabelText("Reference"), "INV-DEMO-0003");
}

describe("CreatePaymentPage", () => {
  it("validates client-side and does not call the API when the form is invalid", async () => {
    const fetchMock = mockFetch({});
    const user = userEvent.setup();
    renderPage();

    await user.click(screen.getByRole("button", { name: "Submit payment" }));
    expect(screen.getByText("Payer account is required")).toBeInTheDocument();
    expect(screen.getByText("Payee account is required")).toBeInTheDocument();
    expect(screen.getByText("Amount is required")).toBeInTheDocument();
    expect(screen.getByText("Reference is required")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Payer account"), "GB12PAYLAB00000001");
    await user.type(screen.getByLabelText("Payee account"), "GB12PAYLAB00000001");
    await user.type(screen.getByLabelText("Amount"), "12.5");
    await user.type(screen.getByLabelText("Reference"), "bad_ref!");
    await user.click(screen.getByRole("button", { name: "Submit payment" }));

    expect(screen.getByText("Payee must differ from payer")).toBeInTheDocument();
    expect(screen.getByText(/exactly 2 decimal places/)).toBeInTheDocument();
    expect(screen.getByText(/1–35 characters/)).toBeInTheDocument();
    expect(screen.getByLabelText("Amount")).toHaveAttribute("aria-invalid", "true");
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("submits a valid payment with Idempotency-Key and X-Demo-Failure headers and shows the 201 result", async () => {
    const fetchMock = mockFetch({
      "/api/v1/payments": () =>
        jsonResponse(PAYMENT, {
          status: 201,
          headers: { Location: `/api/v1/payments/${PAYMENT.paymentId}` },
        }),
    });
    const user = userEvent.setup();
    renderPage();

    await fillValidForm(user);
    await user.type(screen.getByLabelText(/Idempotency-Key/), "order-1001");
    await user.selectOptions(screen.getByTestId("demo-failure-select"), "settlement-timeout");
    await user.click(screen.getByRole("button", { name: "Submit payment" }));

    await waitFor(() => expect(screen.getByTestId("result-success")).toBeInTheDocument());
    expect(screen.getByTestId("result-success")).toHaveTextContent("HTTP 201");
    expect(screen.queryByTestId("result-replayed")).not.toBeInTheDocument();
    const link = screen.getByTestId("result-payment-link");
    expect(link).toHaveAttribute("href", `/payments/${PAYMENT.paymentId}`);
    expect(screen.getByText("GBP 125.50")).toBeInTheDocument();

    const init = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get("Idempotency-Key")).toBe("order-1001");
    expect(headers.get("X-Demo-Failure")).toBe("settlement-timeout");
    expect(JSON.parse(bodyOf(init))).toEqual({
      payerAccount: "GB12PAYLAB00000001",
      payeeAccount: "GB12PAYLAB00000002",
      amount: "125.50",
      currency: "GBP",
      reference: "INV-DEMO-0003",
    });
  });

  it("renders the Idempotent-Replayed header when the backend replays a stored response", async () => {
    mockFetch({
      "/api/v1/payments": () =>
        jsonResponse(PAYMENT, { status: 201, headers: { "Idempotent-Replayed": "true" } }),
    });
    const user = userEvent.setup();
    renderPage();
    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "Submit payment" }));

    await waitFor(() => expect(screen.getByTestId("result-replayed")).toBeInTheDocument());
    expect(screen.getByTestId("result-replayed")).toHaveTextContent("Idempotent-Replayed: true");
    expect(screen.getByTestId("result-success")).toHaveTextContent("HTTP 201");
  });

  it("renders a 409 problem+json conflict with title, detail and type", async () => {
    mockFetch({
      "/api/v1/payments": () =>
        problemResponse(409, {
          title: "Idempotency key reuse",
          detail: "The key was already used with a different request body",
          type: "about:blank#idempotency-key-reuse",
        }),
    });
    const user = userEvent.setup();
    renderPage();
    await fillValidForm(user);
    await user.click(screen.getByRole("button", { name: "Submit payment" }));

    const error = await screen.findByTestId("result-error");
    expect(error).toHaveTextContent("HTTP 409");
    expect(error).toHaveTextContent("Idempotency key reuse");
    expect(error).toHaveTextContent("different request body");
    expect(error).toHaveTextContent("about:blank#idempotency-key-reuse");
    expect(screen.queryByTestId("result-success")).not.toBeInTheDocument();
  });

  it("always shows the demo failure injection select with all three modes", () => {
    mockFetch({});
    renderPage();
    const select = screen.getByTestId("demo-failure-select");
    const options = Array.from(select.querySelectorAll("option")).map((o) => o.value);
    expect(options).toEqual(["", "settlement-timeout", "settlement-rejected", "poison-message"]);
    expect(screen.getByText(/Demo failure injection \(demo profile only\)/)).toBeInTheDocument();
  });
});
