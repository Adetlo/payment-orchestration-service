/**
 * Minimal typed fetch wrapper. Parses RFC 7807 `application/problem+json` bodies into ApiError.
 */

export const API_BASE: string = import.meta.env.VITE_API_BASE ?? "/api/v1";

export interface ApiErrorShape {
  status: number;
  title: string;
  detail: string;
  type: string;
}

export class ApiError extends Error implements ApiErrorShape {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly type: string;
  readonly headers: Headers;

  constructor(shape: ApiErrorShape, headers: Headers = new Headers()) {
    super(shape.detail ? `${shape.title}: ${shape.detail}` : shape.title);
    this.name = "ApiError";
    this.status = shape.status;
    this.title = shape.title;
    this.detail = shape.detail;
    this.type = shape.type;
    this.headers = headers;
  }
}

export interface ApiResponse<T> {
  status: number;
  data: T;
  headers: Headers;
}

export type QueryValue = string | number | boolean | null | undefined;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function asString(value: unknown): string | undefined {
  return typeof value === "string" ? value : undefined;
}

/** Build an ApiError from any non-2xx response. Never throws itself. */
export async function parseProblem(response: Response): Promise<ApiError> {
  const contentType = response.headers.get("content-type") ?? "";
  let body: unknown = null;
  if (contentType.includes("json")) {
    try {
      body = await response.json();
    } catch {
      body = null;
    }
  } else {
    try {
      const text = await response.text();
      body = text.length > 0 ? text : null;
    } catch {
      body = null;
    }
  }

  if (isRecord(body)) {
    const status = typeof body.status === "number" ? body.status : response.status;
    const title = asString(body.title) ?? (response.statusText || `HTTP ${String(status)}`);
    // Bean Validation problems often carry an `errors` array; fold it into the detail.
    let detail = asString(body.detail) ?? "";
    if (Array.isArray(body.errors)) {
      const parts = body.errors
        .map((e) => {
          if (isRecord(e)) {
            const field = asString(e.field);
            const message = asString(e.message) ?? asString(e.defaultMessage) ?? "";
            return field ? `${field}: ${message}` : message;
          }
          return asString(e) ?? "";
        })
        .filter((s) => s.length > 0);
      if (parts.length > 0) {
        detail = detail ? `${detail} (${parts.join("; ")})` : parts.join("; ");
      }
    }
    const type = asString(body.type) ?? "about:blank";
    return new ApiError({ status, title, detail, type }, response.headers);
  }

  return new ApiError(
    {
      status: response.status,
      title: response.statusText || `HTTP ${String(response.status)}`,
      detail: typeof body === "string" ? body.slice(0, 300) : "",
      type: "about:blank",
    },
    response.headers,
  );
}

export function buildUrl(path: string, query?: Record<string, QueryValue>): string {
  const url = path.startsWith("http") ? path : `${API_BASE}${path}`;
  if (!query) return url;
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(query)) {
    if (value === undefined || value === null || value === "") continue;
    params.set(key, String(value));
  }
  const qs = params.toString();
  return qs ? `${url}?${qs}` : url;
}

async function request<T>(
  method: "GET" | "POST",
  path: string,
  options: {
    query?: Record<string, QueryValue>;
    body?: unknown;
    headers?: Record<string, string | undefined>;
    signal?: AbortSignal;
  } = {},
): Promise<ApiResponse<T>> {
  const headers = new Headers({ Accept: "application/json, application/problem+json" });
  if (options.body !== undefined) headers.set("Content-Type", "application/json");
  for (const [k, v] of Object.entries(options.headers ?? {})) {
    if (v !== undefined && v !== "") headers.set(k, v);
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  } catch (cause) {
    const message = cause instanceof Error ? cause.message : "Network error";
    throw new ApiError({ status: 0, title: "Network error", detail: message, type: "about:blank" });
  }

  if (!response.ok) {
    throw await parseProblem(response);
  }

  let data: T;
  if (response.status === 204) {
    data = undefined as T;
  } else {
    const text = await response.text();
    data = (text.length > 0 ? JSON.parse(text) : undefined) as T;
  }
  return { status: response.status, data, headers: response.headers };
}

export async function apiGet<T>(
  path: string,
  query?: Record<string, QueryValue>,
  signal?: AbortSignal,
): Promise<T> {
  const res = await request<T>("GET", path, { query, signal });
  return res.data;
}

export async function apiPost<T>(
  path: string,
  body?: unknown,
  headers?: Record<string, string | undefined>,
): Promise<ApiResponse<T>> {
  return request<T>("POST", path, { body, headers });
}
