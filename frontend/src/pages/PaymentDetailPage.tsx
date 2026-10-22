import { useCallback, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../api/client";
import { cancelPayment, getPayment, getPaymentEvents } from "../api/endpoints";
import type { PaymentResponse } from "../api/types";
import { ErrorBanner } from "../components/ErrorBanner";
import { JsonToggle } from "../components/JsonToggle";
import { StatusPill } from "../components/Pills";
import { Timeline } from "../components/Timeline";
import { usePolling } from "../hooks/usePolling";
import { formatInstant, stringifyDetails } from "../lib/format";
import { formatMoney } from "../lib/money";
import { CANCELLABLE_STATUSES, isTerminal } from "../lib/status";

export function PaymentDetailPage() {
  const { id = "" } = useParams<{ id: string }>();
  const fetchPayment = useCallback(() => getPayment(id), [id]);
  const fetchEvents = useCallback(() => getPaymentEvents(id), [id]);

  const payment = usePolling(fetchPayment, 3000, id.length > 0);
  const events = usePolling(fetchEvents, 3000, id.length > 0);
  const [cancelError, setCancelError] = useState<Error | null>(null);
  const [cancelling, setCancelling] = useState(false);

  const p: PaymentResponse | null = payment.data;
  const canCancel = p !== null && CANCELLABLE_STATUSES.includes(p.status);

  const onCancel = async () => {
    if (!p) return;
    setCancelling(true);
    setCancelError(null);
    try {
      await cancelPayment(p.paymentId, "Cancelled from demo UI");
      payment.refresh();
      events.refresh();
    } catch (cause) {
      setCancelError(cause instanceof Error ? cause : new Error(String(cause)));
    } finally {
      setCancelling(false);
    }
  };

  if (payment.error instanceof ApiError && payment.error.status === 404) {
    return (
      <div className="stack">
        <h1>Payment not found</h1>
        <ErrorBanner error={payment.error} />
        <Link to="/payments">Back to payments</Link>
      </div>
    );
  }

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1 className="row">
            Payment{" "}
            {p && (
              <>
                <code className="mono small" data-testid="payment-id">
                  {p.paymentId}
                </code>
                <StatusPill status={p.status} />
              </>
            )}
          </h1>
          <p>
            {p ? (
              <>
                Reference <code>{p.reference}</code> · polling every 3 s
                {isTerminal(p.status) ? " · terminal state" : ""}
              </>
            ) : (
              "Loading…"
            )}
          </p>
        </div>
        <div className="row">
          <Link to="/payments" className="btn">
            All payments
          </Link>
          {canCancel && (
            <button
              type="button"
              className="btn danger"
              onClick={() => void onCancel()}
              disabled={cancelling}
              data-testid="cancel-payment"
            >
              {cancelling ? "Cancelling…" : "Cancel payment"}
            </button>
          )}
        </div>
      </div>

      <ErrorBanner error={payment.error} />
      <ErrorBanner error={cancelError} testId="cancel-error" />

      <div className="two-col">
        <section className="card" aria-label="Payment detail">
          <div className="card-header">
            <h2>Details</h2>
            {p && <JsonToggle value={p} />}
          </div>
          <div
            className="card-body"
            data-testid="payment-detail"
            data-loaded={p ? "true" : "false"}
          >
            {p ? (
              <dl className="kv">
                <div>
                  <dt>Payment ID</dt>
                  <dd className="mono">{p.paymentId}</dd>
                </div>
                <div>
                  <dt>Status</dt>
                  <dd>
                    <StatusPill status={p.status} />
                  </dd>
                </div>
                <div>
                  <dt>Payer account</dt>
                  <dd className="mono">{p.payerAccount}</dd>
                </div>
                <div>
                  <dt>Payee account</dt>
                  <dd className="mono">{p.payeeAccount}</dd>
                </div>
                <div>
                  <dt>Amount</dt>
                  <dd className="mono">{formatMoney(p.amount, p.currency)}</dd>
                </div>
                <div>
                  <dt>Currency</dt>
                  <dd className="mono">{p.currency}</dd>
                </div>
                <div>
                  <dt>Reference</dt>
                  <dd className="mono">{p.reference}</dd>
                </div>
                <div>
                  <dt>Idempotency key</dt>
                  <dd className="mono">{p.idempotencyKey ?? "—"}</dd>
                </div>
                <div>
                  <dt>Correlation ID</dt>
                  <dd className="mono">{p.correlationId}</dd>
                </div>
                <div>
                  <dt>Demo failure mode</dt>
                  <dd className="mono">{p.demoFailureMode ?? "—"}</dd>
                </div>
                <div>
                  <dt>Created</dt>
                  <dd className="mono">{formatInstant(p.createdAt)}</dd>
                </div>
                <div>
                  <dt>Updated</dt>
                  <dd className="mono">{formatInstant(p.updatedAt)}</dd>
                </div>
              </dl>
            ) : (
              <div className="loading">Loading payment…</div>
            )}
          </div>
        </section>

        <section className="card" aria-label="Retries and failure">
          <div className="card-header">
            <h2>Retries and failure</h2>
          </div>
          <div className="card-body" data-testid="payment-retries">
            {p ? (
              <dl className="kv">
                <div>
                  <dt>Retry count</dt>
                  <dd data-testid="retry-count">
                    {p.retryCount} / {p.maxRetries}
                  </dd>
                </div>
                <div>
                  <dt>Next retry at</dt>
                  <dd className="mono">{formatInstant(p.nextRetryAt)}</dd>
                </div>
                <div>
                  <dt>Failure reason</dt>
                  <dd>{p.failureReason ?? "—"}</dd>
                </div>
                <div>
                  <dt>Terminal</dt>
                  <dd>{isTerminal(p.status) ? "yes" : "no"}</dd>
                </div>
              </dl>
            ) : (
              <div className="loading">Loading…</div>
            )}
          </div>
        </section>
      </div>

      <div className="two-col" data-testid="payment-events">
        <section className="card" aria-label="Event timeline">
          <div className="card-header">
            <h2>Event timeline</h2>
            <span className="muted small">
              {events.data ? `${String(events.data.events.length)} events · oldest first` : ""}
            </span>
          </div>
          <div
            className="card-body"
            data-testid="payment-timeline"
            data-count={events.data?.events.length ?? 0}
          >
            <ErrorBanner error={events.error} />
            {events.data ? (
              <Timeline events={events.data.events} />
            ) : (
              <div className="loading">Loading events…</div>
            )}
          </div>
        </section>

        <section className="card" aria-label="Audit trail">
          <div className="card-header">
            <h2>Audit trail</h2>
            <span className="muted small">append-only</span>
          </div>
          <div className="card-body flush table-wrap" data-testid="payment-audit">
            <table className="table">
              <thead>
                <tr>
                  <th scope="col">Time</th>
                  <th scope="col">Action</th>
                  <th scope="col">Actor</th>
                  <th scope="col">Details</th>
                </tr>
              </thead>
              <tbody>
                {(events.data?.audit ?? []).length === 0 && (
                  <tr>
                    <td colSpan={4} className="table-empty">
                      {events.data ? "No audit entries." : "Loading…"}
                    </td>
                  </tr>
                )}
                {(events.data?.audit ?? []).map((a) => (
                  <tr key={String(a.id)} data-testid="audit-row">
                    <td className="mono">{formatInstant(a.occurredAt)}</td>
                    <td className="mono">{a.action}</td>
                    <td>{a.actor}</td>
                    <td className="mono wrap">{stringifyDetails(a.details)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      </div>
    </div>
  );
}
