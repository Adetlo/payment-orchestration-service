import { useCallback } from "react";
import { useSearchParams } from "react-router-dom";
import { listPayments } from "../api/endpoints";
import { CURRENCIES, PAYMENT_STATUSES } from "../api/types";
import { ErrorBanner } from "../components/ErrorBanner";
import { Pagination } from "../components/Pagination";
import { PaymentsTable } from "../components/PaymentsTable";
import { usePolling } from "../hooks/usePolling";
import { localInputToIso } from "../lib/format";

const PAGE_SIZE = 20;

export function PaymentsPage() {
  const [params, setParams] = useSearchParams();
  const status = params.get("status") ?? "";
  const currency = params.get("currency") ?? "";
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const page = Math.max(0, Number(params.get("page") ?? "0") || 0);

  const load = useCallback(
    () =>
      listPayments({
        status: status || undefined,
        currency: currency || undefined,
        from: localInputToIso(from),
        to: localInputToIso(to),
        page,
        size: PAGE_SIZE,
      }),
    [status, currency, from, to, page],
  );
  const { data, error, loading, refresh } = usePolling(load, 0);

  const setParam = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next, { replace: true });
  };

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Payments</h1>
          <p>Newest first. Filters map directly onto the list endpoint query parameters.</p>
        </div>
      </div>

      <div className="card">
        <div className="card-body filter-bar" data-testid="payments-filters">
          <div className="field">
            <label htmlFor="filter-status">Status</label>
            <select
              id="filter-status"
              className="select"
              value={status}
              onChange={(e) => setParam("status", e.target.value)}
            >
              <option value="">All</option>
              {PAYMENT_STATUSES.map((s) => (
                <option key={s} value={s}>
                  {s}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-currency">Currency</label>
            <select
              id="filter-currency"
              className="select"
              value={currency}
              onChange={(e) => setParam("currency", e.target.value)}
            >
              <option value="">All</option>
              {CURRENCIES.map((c) => (
                <option key={c} value={c}>
                  {c}
                </option>
              ))}
            </select>
          </div>
          <div className="field">
            <label htmlFor="filter-from">Created from</label>
            <input
              id="filter-from"
              className="input"
              type="datetime-local"
              value={from}
              onChange={(e) => setParam("from", e.target.value)}
            />
          </div>
          <div className="field">
            <label htmlFor="filter-to">Created to</label>
            <input
              id="filter-to"
              className="input"
              type="datetime-local"
              value={to}
              onChange={(e) => setParam("to", e.target.value)}
            />
          </div>
          <div className="field">
            <span className="hint">
              {data ? `${String(data.totalElements)} matching` : loading ? "Loading…" : ""}
            </span>
          </div>
          <div className="row">
            <button type="button" className="btn" onClick={() => setParams({}, { replace: true })}>
              Clear
            </button>
            <button type="button" className="btn" onClick={refresh}>
              Refresh
            </button>
          </div>
        </div>
      </div>

      <ErrorBanner error={error} />

      <div className="card">
        <PaymentsTable
          payments={data?.items ?? []}
          testId="payments-table"
          emptyMessage={loading ? "Loading…" : "No payments match these filters."}
        />
        <Pagination
          page={data?.page ?? page}
          size={data?.size ?? PAGE_SIZE}
          totalPages={data?.totalPages ?? 0}
          totalElements={data?.totalElements ?? 0}
          onChange={(p) => setParam("page", String(p))}
        />
      </div>
    </div>
  );
}
