import { useCallback, useState } from "react";
import { Link } from "react-router-dom";
import { getReconciliationRun, listReconciliationRuns, runReconciliation } from "../api/endpoints";
import type { ReconciliationRunDetailResponse, ReconciliationScenario } from "../api/types";
import { ClassificationBar } from "../components/ClassificationBar";
import { ErrorBanner } from "../components/ErrorBanner";
import { ClassificationPill, RunStatusPill } from "../components/Pills";
import { usePolling } from "../hooks/usePolling";
import { formatInstant, formatNumber, shortId, stringifyDetails } from "../lib/format";
import { formatAmount } from "../lib/money";

const fetchRuns = () => listReconciliationRuns(0, 10);

export function ReconciliationPage() {
  const runsState = usePolling(fetchRuns, 0);
  const runs = runsState.data?.items ?? [];
  const loadingRuns = runsState.loading;
  const [scenario, setScenario] = useState<ReconciliationScenario>("with-discrepancies");
  const [running, setRunning] = useState(false);
  const [runError, setRunError] = useState<Error | null>(null);
  const [chosenId, setChosenId] = useState<string | null>(null);
  const selectedId = chosenId ?? runs[0]?.runId ?? null;

  const fetchSelected = useCallback(
    () =>
      selectedId
        ? getReconciliationRun(selectedId)
        : Promise.resolve<ReconciliationRunDetailResponse | null>(null),
    [selectedId],
  );
  const detail = usePolling(fetchSelected, 0, selectedId !== null);
  const selected = detail.data?.runId === selectedId ? detail.data : null;
  const selectedError = detail.error;
  const setSelectedId = setChosenId;

  const onRun = async () => {
    setRunning(true);
    setRunError(null);
    try {
      const response = await runReconciliation({ scenario, seed: 42 });
      setChosenId(response.data.runId);
      runsState.refresh();
    } catch (cause) {
      setRunError(cause instanceof Error ? cause : new Error(String(cause)));
    } finally {
      setRunning(false);
    }
  };

  const lastRun = runs[0] ?? null;

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Reconciliation</h1>
          <p>
            Compares internal COMPLETED/FAILED payments against a synthetic settlement feed. The{" "}
            <code>with-discrepancies</code> scenario injects deterministic mismatches.
          </p>
        </div>
        <div className="row">
          <label htmlFor="scenario" className="small">
            Scenario
          </label>
          <select
            id="scenario"
            className="select"
            style={{ width: "auto" }}
            value={scenario}
            onChange={(e) => setScenario(e.target.value as ReconciliationScenario)}
            data-testid="scenario-select"
          >
            <option value="clean">clean</option>
            <option value="with-discrepancies">with-discrepancies</option>
          </select>
          <button
            type="button"
            className="btn primary"
            onClick={() => void onRun()}
            disabled={running}
            data-testid="run-reconciliation"
          >
            {running ? "Running…" : "Run reconciliation"}
          </button>
        </div>
      </div>

      <ErrorBanner error={runError ?? runsState.error} />

      <section className="card" aria-label="Last run summary">
        <div className="card-header">
          <h2>Last run</h2>
          {lastRun && <RunStatusPill status={lastRun.status} />}
        </div>
        <div
          className="card-body"
          data-testid="reconciliation-summary"
          data-loaded={lastRun ? "true" : "false"}
        >
          {lastRun ? (
            <div className="stack">
              <dl
                className="kv"
                style={{ gridTemplateColumns: "repeat(auto-fit, minmax(170px, 1fr))" }}
              >
                <div>
                  <dt>Scenario</dt>
                  <dd className="mono">{lastRun.scenario}</dd>
                </div>
                <div>
                  <dt>Started</dt>
                  <dd className="mono">{formatInstant(lastRun.startedAt)}</dd>
                </div>
                <div>
                  <dt>Records checked</dt>
                  <dd data-testid="summary-checked">{formatNumber(lastRun.recordsChecked)}</dd>
                </div>
                <div>
                  <dt>Matched</dt>
                  <dd>{formatNumber(lastRun.matched)}</dd>
                </div>
                <div>
                  <dt>Unmatched</dt>
                  <dd>{formatNumber(lastRun.unmatched)}</dd>
                </div>
                <div>
                  <dt>Status</dt>
                  <dd>
                    <RunStatusPill status={lastRun.status} />
                  </dd>
                </div>
              </dl>
              <ClassificationBar byClassification={lastRun.byClassification} />
            </div>
          ) : (
            <div className="loading">
              {loadingRuns ? "Loading…" : "No reconciliation runs yet. Run one above."}
            </div>
          )}
        </div>
      </section>

      <section className="card" aria-label="Runs">
        <div className="card-header">
          <h2>Runs</h2>
          <span className="muted small">newest first · click a run to inspect its items</span>
        </div>
        <div className="table-wrap" data-testid="reconciliation-runs">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Run</th>
                <th scope="col">Scenario</th>
                <th scope="col">Status</th>
                <th scope="col">Started</th>
                <th scope="col">Completed</th>
                <th scope="col" className="num">
                  Checked
                </th>
                <th scope="col" className="num">
                  Matched
                </th>
                <th scope="col" className="num">
                  Unmatched
                </th>
              </tr>
            </thead>
            <tbody>
              {runs.length === 0 && (
                <tr>
                  <td colSpan={8} className="table-empty">
                    {loadingRuns ? "Loading…" : "No runs."}
                  </td>
                </tr>
              )}
              {runs.map((run) => (
                <tr
                  key={run.runId}
                  className={`selectable${run.runId === selectedId ? " selected" : ""}`}
                  onClick={() => setSelectedId(run.runId)}
                  data-testid="run-row"
                >
                  <td>
                    <button
                      type="button"
                      className="btn small"
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedId(run.runId);
                      }}
                      aria-pressed={run.runId === selectedId}
                      title={run.runId}
                    >
                      {shortId(run.runId)}
                    </button>
                  </td>
                  <td className="mono">{run.scenario}</td>
                  <td>
                    <RunStatusPill status={run.status} />
                  </td>
                  <td className="mono">{formatInstant(run.startedAt)}</td>
                  <td className="mono">{formatInstant(run.completedAt)}</td>
                  <td className="num">{formatNumber(run.recordsChecked)}</td>
                  <td className="num">{formatNumber(run.matched)}</td>
                  <td className="num">{formatNumber(run.unmatched)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="card" aria-label="Run items">
        <div className="card-header">
          <h2>
            Items{" "}
            {selected ? <span className="muted small">· run {shortId(selected.runId)}</span> : null}
          </h2>
          {selected && (
            <span className="muted small">{formatNumber(selected.items.length)} items</span>
          )}
        </div>
        <ErrorBanner error={selectedError} />
        <div className="table-wrap" data-testid="reconciliation-items">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Classification</th>
                <th scope="col">Payment</th>
                <th scope="col">External ref</th>
                <th scope="col" className="num">
                  Internal amount
                </th>
                <th scope="col" className="num">
                  External amount
                </th>
                <th scope="col">Internal status</th>
                <th scope="col">External status</th>
                <th scope="col">Details</th>
              </tr>
            </thead>
            <tbody>
              {(!selected || selected.items.length === 0) && (
                <tr>
                  <td colSpan={8} className="table-empty">
                    {selectedId && !selected ? "Loading…" : "Select a run to see its items."}
                  </td>
                </tr>
              )}
              {selected?.items.map((item) => (
                <tr key={String(item.id)} data-testid="item-row">
                  <td>
                    <ClassificationPill classification={item.classification} />
                  </td>
                  <td>
                    {item.paymentId ? (
                      <Link
                        className="mono"
                        to={`/payments/${item.paymentId}`}
                        title={item.paymentId}
                      >
                        {shortId(item.paymentId)}
                      </Link>
                    ) : (
                      "—"
                    )}
                  </td>
                  <td className="mono">{item.externalReference ?? "—"}</td>
                  <td className="num mono">{formatAmount(item.internalAmount)}</td>
                  <td className="num mono">{formatAmount(item.externalAmount)}</td>
                  <td className="mono">{item.internalStatus ?? "—"}</td>
                  <td className="mono">{item.externalStatus ?? "—"}</td>
                  <td className="mono wrap">{stringifyDetails(item.details)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </div>
  );
}
