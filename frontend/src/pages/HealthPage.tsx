import { getSystemHealth } from "../api/endpoints";
import type { HealthComponentName } from "../api/types";
import { ErrorBanner } from "../components/ErrorBanner";
import { JsonToggle } from "../components/JsonToggle";
import { HealthPill } from "../components/Pills";
import { usePolling } from "../hooks/usePolling";
import { formatInstant } from "../lib/format";

const COMPONENTS: { key: HealthComponentName; label: string; description: string }[] = [
  { key: "api", label: "API", description: "Spring Boot service and request pipeline" },
  { key: "postgresql", label: "PostgreSQL", description: "Payments, events, outbox, audit" },
  { key: "kafka", label: "Kafka", description: "payment.events.v1 broker (KRaft)" },
  {
    key: "redis",
    label: "Redis",
    description: "Idempotency lock and rate limit (degrades safely)",
  },
];

const fetchHealth = () => getSystemHealth();

export function HealthPage() {
  const health = usePolling(fetchHealth);
  const data = health.data;

  return (
    <div className="stack">
      <div className="page-header">
        <div>
          <h1 className="row">
            System health <HealthPill status={data?.status} />
          </h1>
          <p>
            From <code>GET /api/v1/system/health</code> (always HTTP 200 so the UI can render
            degraded states) · polling every 3 s
            {health.lastUpdated
              ? ` · last update ${formatInstant(health.lastUpdated.toISOString())}`
              : ""}
          </p>
        </div>
      </div>

      <ErrorBanner error={health.error} />

      <section
        className="health-grid"
        data-testid="system-health"
        data-loaded={data ? "true" : "false"}
        aria-label="Components"
      >
        {COMPONENTS.map((c) => {
          const component = data?.components[c.key];
          return (
            <div className="card health-card" key={c.key} data-testid={`health-${c.key}`}>
              <div className="row" style={{ justifyContent: "space-between" }}>
                <span className="name">{c.label}</span>
                <HealthPill status={component?.status ?? (data ? "UNKNOWN" : undefined)} />
              </div>
              <span className="detail">{c.description}</span>
              <span className="detail mono" data-testid={`health-${c.key}-detail`}>
                {component?.detail ?? (data ? "no detail reported" : "Loading…")}
              </span>
            </div>
          );
        })}
      </section>

      <section className="card" aria-label="Raw response">
        <div className="card-header">
          <h2>Raw response</h2>
          {data && <JsonToggle value={data} />}
        </div>
      </section>
    </div>
  );
}
