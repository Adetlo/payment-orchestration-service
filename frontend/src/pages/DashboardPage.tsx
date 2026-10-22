import { Link } from "react-router-dom";
import { getSystemHealth, getSystemStats, listPayments } from "../api/endpoints";
import { ErrorBanner } from "../components/ErrorBanner";
import { PaymentsTable } from "../components/PaymentsTable";
import { HealthPill, RunStatusPill } from "../components/Pills";
import { StatCard } from "../components/StatCard";
import { usePolling } from "../hooks/usePolling";
import { formatInstant, formatNumber } from "../lib/format";
import { aggregateStats } from "../lib/stats";

const fetchStats = () => getSystemStats();
const fetchHealth = () => getSystemHealth();
const fetchRecent = () => listPayments({ page: 0, size: 8 });

export function DashboardPage() {
  const stats = usePolling(fetchStats);
  const health = usePolling(fetchHealth);
  const recent = usePolling(fetchRecent);

  const agg = aggregateStats(stats.data);
  const lastRun = stats.data?.reconciliation.lastRun ?? null;
  const outbox = stats.data?.outbox;
  const kafka = stats.data?.kafka;
  const components = health.data?.components ?? {};
  const downComponents = Object.entries(components)
    .filter(([, c]) => c.status !== "UP")
    .map(([name, c]) => `${name} ${c.status}`);

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1>Dashboard</h1>
          <p>Live view of the orchestration pipeline. Refreshes every 3 seconds.</p>
        </div>
        <Link to="/payments/new" className="btn primary">
          New payment
        </Link>
      </div>

      <ErrorBanner error={stats.error ?? health.error} />

      <section
        className="stat-grid"
        data-testid="dashboard-stats"
        data-loaded={stats.data && health.data ? "true" : "false"}
        aria-label="Payment statistics"
      >
        <StatCard
          label="Total payments"
          value={formatNumber(agg.total)}
          detail={`${formatNumber(agg.cancelled)} cancelled`}
          testId="stat-total"
        />
        <StatCard
          label="Successful"
          value={formatNumber(agg.successful)}
          detail="COMPLETED"
          tone="green"
          testId="stat-successful"
        />
        <StatCard
          label="Failed"
          value={formatNumber(agg.failed)}
          detail="FAILED + REJECTED"
          tone="red"
          testId="stat-failed"
        />
        <StatCard
          label="Pending"
          value={formatNumber(agg.pending)}
          detail="RECEIVED · VALIDATED · PROCESSING · RETRY_PENDING"
          tone="amber"
          testId="stat-pending"
        />
        <StatCard
          label="Reconciliation"
          value={lastRun ? <RunStatusPill status={lastRun.status} /> : "No runs"}
          detail={
            lastRun
              ? `${formatNumber(lastRun.matched)} matched · ${formatNumber(lastRun.unmatched)} unmatched · ${formatInstant(lastRun.startedAt)}`
              : "Run one from the Reconciliation page"
          }
          small
          testId="stat-reconciliation"
        />
        <StatCard
          label="Outbox"
          value={formatNumber(outbox?.pending)}
          detail={`pending · ${formatNumber(outbox?.published)} published · ${formatNumber(outbox?.failed)} failed`}
          small
          testId="stat-outbox"
        />
        <StatCard
          label="Kafka events"
          value={formatNumber(kafka?.publishedTotal)}
          detail={`published · ${formatNumber(kafka?.deadLetterTotal)} dead-lettered`}
          small
          testId="stat-kafka"
        />
        <StatCard
          label="Service health"
          value={<HealthPill status={health.data?.status} />}
          detail={downComponents.length > 0 ? downComponents.join(", ") : "All components UP"}
          small
          testId="stat-health"
        />
      </section>

      <section className="card" aria-label="Recent payments">
        <div className="card-header">
          <h2>Recent payments</h2>
          <Link to="/payments" className="small">
            View all
          </Link>
        </div>
        <div className="card-body flush">
          <ErrorBanner error={recent.error} />
          <PaymentsTable
            payments={recent.data?.items ?? []}
            testId="dashboard-recent"
            emptyMessage={recent.loading ? "Loading…" : "No payments yet."}
          />
        </div>
      </section>
    </div>
  );
}
