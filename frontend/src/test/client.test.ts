import { describe, expect, it, vi } from "vitest";
import { ApiError, apiGet, apiPost, buildUrl, parseProblem } from "../api/client";
import { bodyOf, jsonResponse, mockFetch, problemResponse, urlOf } from "./fixtures";

describe("api client", () => {
  it("parses application/problem+json into an ApiError with status/title/detail/type", async () => {
    const error = await parseProblem(
      problemResponse(409, {
        title: "Idempotency key reuse",
        detail: "Key order-1001 was used with a different request body",
        type: "about:blank#idempotency-key-reuse",
      }),
    );
    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.title).toBe("Idempotency key reuse");
    expect(error.detail).toContain("different request body");
    expect(error.type).toBe("about:blank#idempotency-key-reuse");
    expect(error.message).toContain("Idempotency key reuse");
  });

  it("folds Bean Validation `errors` entries into the detail", async () => {
    const error = await parseProblem(
      problemResponse(400, {
        title: "Validation failed",
        detail: "Request body is invalid",
        errors: [{ field: "amount", message: "must have scale 2" }],
      }),
    );
    expect(error.status).toBe(400);
    expect(error.detail).toBe("Request body is invalid (amount: must have scale 2)");
  });

  it("falls back to statusText for non-JSON error bodies", async () => {
    const error = await parseProblem(
      new Response("<html>Bad Gateway</html>", {
        status: 502,
        statusText: "Bad Gateway",
        headers: { "content-type": "text/html" },
      }),
    );
    expect(error.status).toBe(502);
    expect(error.title).toBe("Bad Gateway");
    expect(error.type).toBe("about:blank");
  });

  it("apiGet builds the URL from the base path and drops empty query values", async () => {
    const fetchMock = mockFetch({
      "/payments": () =>
        jsonResponse({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    });
    const page = await apiGet<{ items: unknown[] }>("/payments", {
      status: "COMPLETED",
      currency: "",
      from: undefined,
      page: 0,
      size: 20,
    });
    expect(page.items).toEqual([]);
    const calledUrl = urlOf(fetchMock.mock.calls[0]?.[0]);
    expect(calledUrl).toBe("/api/v1/payments?status=COMPLETED&page=0&size=20");
    expect(buildUrl("/x", { a: null, b: "1" })).toBe("/api/v1/x?b=1");
  });

  it("apiPost sends JSON with optional headers and exposes status + response headers", async () => {
    const fetchMock = mockFetch({
      "/payments": () =>
        jsonResponse(
          { paymentId: "p1" },
          {
            status: 201,
            headers: { "Idempotent-Replayed": "true", Location: "/api/v1/payments/p1" },
          },
        ),
    });
    const res = await apiPost<{ paymentId: string }>(
      "/payments",
      { amount: "1.00" },
      { "Idempotency-Key": "k1", "X-Demo-Failure": undefined },
    );
    expect(res.status).toBe(201);
    expect(res.data.paymentId).toBe("p1");
    expect(res.headers.get("Idempotent-Replayed")).toBe("true");
    const init = fetchMock.mock.calls[0]?.[1];
    const headers = new Headers(init?.headers);
    expect(headers.get("Idempotency-Key")).toBe("k1");
    expect(headers.has("X-Demo-Failure")).toBe(false);
    expect(headers.get("Content-Type")).toBe("application/json");
    expect(bodyOf(init)).toBe(JSON.stringify({ amount: "1.00" }));
  });

  it("throws ApiError on non-2xx and wraps network failures with status 0", async () => {
    mockFetch({ "/payments/missing": () => problemResponse(404, { title: "Not Found" }) });
    await expect(apiGet("/payments/missing")).rejects.toMatchObject({
      status: 404,
      title: "Not Found",
    });

    vi.stubGlobal(
      "fetch",
      vi.fn(() => Promise.reject(new TypeError("Failed to fetch"))),
    );
    await expect(apiGet("/system/health")).rejects.toMatchObject({
      status: 0,
      title: "Network error",
      detail: "Failed to fetch",
    });
  });
});
