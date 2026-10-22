import { Link } from "react-router-dom";
import type { PaymentResponse } from "../api/types";
import { formatInstant, maskAccount, shortId } from "../lib/format";
import { formatMoney } from "../lib/money";
import { StatusPill } from "./Pills";

interface PaymentsTableProps {
  payments: PaymentResponse[];
  testId?: string;
  emptyMessage?: string;
  compact?: boolean;
}

export function PaymentsTable({
  payments,
  testId,
  emptyMessage = "No payments yet.",
  compact = false,
}: PaymentsTableProps) {
  return (
    <div className="table-wrap" data-testid={testId}>
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Payment</th>
            <th scope="col">Reference</th>
            <th scope="col">Status</th>
            <th scope="col" className="num">
              Amount
            </th>
            {!compact && <th scope="col">Payer</th>}
            {!compact && <th scope="col">Payee</th>}
            <th scope="col" className="num">
              Retries
            </th>
            <th scope="col">Created</th>
          </tr>
        </thead>
        <tbody>
          {payments.length === 0 && (
            <tr>
              <td colSpan={compact ? 6 : 8} className="table-empty">
                {emptyMessage}
              </td>
            </tr>
          )}
          {payments.map((p) => (
            <tr key={p.paymentId} data-testid="payment-row" data-payment-id={p.paymentId}>
              <td>
                <Link className="mono" to={`/payments/${p.paymentId}`} title={p.paymentId}>
                  {shortId(p.paymentId)}
                </Link>
              </td>
              <td className="mono">{p.reference}</td>
              <td>
                <StatusPill status={p.status} />
              </td>
              <td className="num mono">{formatMoney(p.amount, p.currency)}</td>
              {!compact && <td className="mono">{maskAccount(p.payerAccount)}</td>}
              {!compact && <td className="mono">{maskAccount(p.payeeAccount)}</td>}
              <td className="num">
                {p.retryCount}/{p.maxRetries}
              </td>
              <td className="mono">{formatInstant(p.createdAt)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
