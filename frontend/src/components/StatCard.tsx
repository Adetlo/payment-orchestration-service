import type { ReactNode } from "react";
import type { Tone } from "../lib/status";

interface StatCardProps {
  label: string;
  value: ReactNode;
  detail?: ReactNode;
  tone?: Tone;
  small?: boolean;
  testId?: string;
}

export function StatCard({ label, value, detail, tone, small, testId }: StatCardProps) {
  return (
    <div className={`card stat-card${tone ? ` tone-${tone}` : ""}`} data-testid={testId}>
      <span className="stat-label">{label}</span>
      <span
        className={`stat-value${small ? " small" : ""}`}
        data-testid={testId && `${testId}-value`}
      >
        {value}
      </span>
      {detail !== undefined && <span className="stat-detail">{detail}</span>}
    </div>
  );
}
