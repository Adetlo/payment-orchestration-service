import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { ApiError } from "../api/client";
import { createPayment } from "../api/endpoints";
import { CURRENCIES, DEMO_FAILURE_MODES, type PaymentResponse } from "../api/types";
import { StatusPill } from "../components/Pills";
import { formatMoney } from "../lib/money";
import {
  validateCreatePayment,
  type CreatePaymentFormValues,
  type FormErrors,
} from "../lib/validation";

export interface CreatePaymentResult {
  status: number;
  replayed: boolean;
  payment: PaymentResponse;
  location: string | null;
}

const INITIAL: CreatePaymentFormValues = {
  payerAccount: "",
  payeeAccount: "",
  amount: "",
  currency: "GBP",
  reference: "",
  idempotencyKey: "",
  demoFailure: "",
};

export function CreatePaymentPage() {
  const [values, setValues] = useState<CreatePaymentFormValues>(INITIAL);
  const [errors, setErrors] = useState<FormErrors>({});
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<CreatePaymentResult | null>(null);
  const [apiError, setApiError] = useState<ApiError | null>(null);

  const update = (field: keyof CreatePaymentFormValues) => (value: string) => {
    setValues((v) => ({ ...v, [field]: value }));
    setErrors((e) => ({ ...e, [field]: undefined }));
  };

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const nextErrors = validateCreatePayment(values);
    setErrors(nextErrors);
    if (Object.values(nextErrors).some(Boolean)) return;

    setSubmitting(true);
    setResult(null);
    setApiError(null);
    try {
      const response = await createPayment(
        {
          payerAccount: values.payerAccount.trim(),
          payeeAccount: values.payeeAccount.trim(),
          amount: values.amount.trim(),
          currency: values.currency,
          reference: values.reference.trim(),
        },
        {
          idempotencyKey: values.idempotencyKey.trim() || undefined,
          demoFailure: values.demoFailure || undefined,
        },
      );
      setResult({
        status: response.status,
        replayed: (response.headers.get("Idempotent-Replayed") ?? "").toLowerCase() === "true",
        payment: response.data,
        location: response.headers.get("Location"),
      });
    } catch (cause) {
      if (cause instanceof ApiError) setApiError(cause);
      else
        setApiError(
          new ApiError({
            status: 0,
            title: "Unexpected error",
            detail: cause instanceof Error ? cause.message : String(cause),
            type: "about:blank",
          }),
        );
    } finally {
      setSubmitting(false);
    }
  };

  const errorId = (field: keyof CreatePaymentFormValues) =>
    errors[field] ? `${field}-error` : undefined;

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Create payment</h1>
          <p>
            Submits <code>POST /api/v1/payments</code>. Accounts are synthetic IBAN-like
            identifiers; nothing here touches a real network.
          </p>
        </div>
      </div>

      <div className="two-col">
        <form
          className="card"
          data-testid="create-payment-form"
          onSubmit={onSubmit}
          noValidate
          aria-label="Create payment"
        >
          <div className="card-body form-grid">
            <div className="field">
              <label htmlFor="payerAccount">Payer account</label>
              <input
                id="payerAccount"
                name="payerAccount"
                className="input mono"
                placeholder="GB12PAYLAB00000001"
                autoComplete="off"
                value={values.payerAccount}
                onChange={(e) => update("payerAccount")(e.target.value.toUpperCase())}
                aria-invalid={Boolean(errors.payerAccount)}
                aria-describedby={errorId("payerAccount")}
              />
              {errors.payerAccount && (
                <span className="error" id="payerAccount-error">
                  {errors.payerAccount}
                </span>
              )}
            </div>
            <div className="field">
              <label htmlFor="payeeAccount">Payee account</label>
              <input
                id="payeeAccount"
                name="payeeAccount"
                className="input mono"
                placeholder="GB12PAYLAB00000002"
                autoComplete="off"
                value={values.payeeAccount}
                onChange={(e) => update("payeeAccount")(e.target.value.toUpperCase())}
                aria-invalid={Boolean(errors.payeeAccount)}
                aria-describedby={errorId("payeeAccount")}
              />
              {errors.payeeAccount && (
                <span className="error" id="payeeAccount-error">
                  {errors.payeeAccount}
                </span>
              )}
            </div>
            <div className="field">
              <label htmlFor="amount">Amount</label>
              <input
                id="amount"
                name="amount"
                className="input mono"
                inputMode="decimal"
                placeholder="125.50"
                autoComplete="off"
                value={values.amount}
                onChange={(e) => update("amount")(e.target.value)}
                aria-invalid={Boolean(errors.amount)}
                aria-describedby={errorId("amount")}
              />
              <span className="hint">
                Decimal string; 2 decimals (0 for JPY). Max 1,000,000.00.
              </span>
              {errors.amount && (
                <span className="error" id="amount-error">
                  {errors.amount}
                </span>
              )}
            </div>
            <div className="field">
              <label htmlFor="currency">Currency</label>
              <select
                id="currency"
                name="currency"
                className="select"
                value={values.currency}
                onChange={(e) => update("currency")(e.target.value)}
              >
                {CURRENCIES.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
            <div className="field span-2">
              <label htmlFor="reference">Reference</label>
              <input
                id="reference"
                name="reference"
                className="input mono"
                placeholder="INV-1001"
                maxLength={35}
                autoComplete="off"
                value={values.reference}
                onChange={(e) => update("reference")(e.target.value)}
                aria-invalid={Boolean(errors.reference)}
                aria-describedby={errorId("reference")}
              />
              {errors.reference && (
                <span className="error" id="reference-error">
                  {errors.reference}
                </span>
              )}
            </div>
            <div className="field">
              <label htmlFor="idempotencyKey">Idempotency-Key (optional)</label>
              <input
                id="idempotencyKey"
                name="idempotencyKey"
                className="input mono"
                placeholder="order-1001"
                maxLength={128}
                autoComplete="off"
                value={values.idempotencyKey}
                onChange={(e) => update("idempotencyKey")(e.target.value)}
                aria-invalid={Boolean(errors.idempotencyKey)}
                aria-describedby={errorId("idempotencyKey")}
              />
              <span className="hint">
                Resubmitting the same key and body replays the stored 201 response.
              </span>
              {errors.idempotencyKey && (
                <span className="error" id="idempotencyKey-error">
                  {errors.idempotencyKey}
                </span>
              )}
            </div>
            <div className="field">
              <label htmlFor="demoFailure">Demo failure injection (demo profile only)</label>
              <select
                id="demoFailure"
                name="demoFailure"
                className="select"
                value={values.demoFailure}
                onChange={(e) => update("demoFailure")(e.target.value)}
                data-testid="demo-failure-select"
              >
                <option value="">None (normal settlement)</option>
                {DEMO_FAILURE_MODES.map((mode) => (
                  <option key={mode} value={mode}>
                    {mode}
                  </option>
                ))}
              </select>
              <span className="hint">
                Sent as <code>X-Demo-Failure</code>; ignored unless the backend runs the demo
                profile.
              </span>
            </div>
            <div className="span-2 row">
              <button type="submit" className="btn primary" disabled={submitting}>
                {submitting ? "Submitting…" : "Submit payment"}
              </button>
              <button
                type="button"
                className="btn"
                onClick={() => {
                  setValues(INITIAL);
                  setErrors({});
                  setResult(null);
                  setApiError(null);
                }}
              >
                Reset
              </button>
            </div>
          </div>
        </form>

        <section className="card" aria-label="Result" data-testid="create-payment-result">
          <div className="card-header">
            <h2>Response</h2>
          </div>
          <div className="card-body stack">
            {!result && !apiError && (
              <p className="muted small" style={{ margin: 0 }}>
                The HTTP status, idempotency headers and the created payment will appear here.
              </p>
            )}
            {result && (
              <>
                <div className="banner tone-green" data-testid="result-success">
                  <strong>HTTP {result.status}</strong>
                  {result.replayed ? (
                    <div data-testid="result-replayed">
                      <code>Idempotent-Replayed: true</code> — stored response returned, no new
                      payment created.
                    </div>
                  ) : (
                    <div>Payment accepted.</div>
                  )}
                </div>
                <dl className="kv">
                  <div>
                    <dt>Payment ID</dt>
                    <dd>
                      <Link
                        className="mono"
                        to={`/payments/${result.payment.paymentId}`}
                        data-testid="result-payment-link"
                      >
                        {result.payment.paymentId}
                      </Link>
                    </dd>
                  </div>
                  <div>
                    <dt>Status</dt>
                    <dd>
                      <StatusPill status={result.payment.status} />
                    </dd>
                  </div>
                  <div>
                    <dt>Amount</dt>
                    <dd className="mono">
                      {formatMoney(result.payment.amount, result.payment.currency)}
                    </dd>
                  </div>
                  <div>
                    <dt>Reference</dt>
                    <dd className="mono">{result.payment.reference}</dd>
                  </div>
                  <div>
                    <dt>Correlation ID</dt>
                    <dd className="mono">{result.payment.correlationId}</dd>
                  </div>
                  <div>
                    <dt>Location</dt>
                    <dd className="mono">{result.location ?? "—"}</dd>
                  </div>
                </dl>
              </>
            )}
            {apiError && (
              <div className="banner tone-red" role="alert" data-testid="result-error">
                <strong>
                  {apiError.status > 0 ? `HTTP ${String(apiError.status)} · ` : ""}
                  {apiError.title}
                </strong>
                {apiError.detail && <div>{apiError.detail}</div>}
                {apiError.type && apiError.type !== "about:blank" && (
                  <div className="mono">{apiError.type}</div>
                )}
              </div>
            )}
          </div>
        </section>
      </div>
    </div>
  );
}
