import {
  classificationTone,
  healthTone,
  paymentStatusTone,
  reconciliationStatusTone,
  type Tone,
} from "../lib/status";

interface PillProps {
  tone: Tone;
  label: string;
  testId?: string;
  title?: string;
}

export function Pill({ tone, label, testId, title }: PillProps) {
  return (
    <span className={`pill tone-${tone}`} data-testid={testId} data-tone={tone} title={title}>
      {label}
    </span>
  );
}

export function StatusPill({ status }: { status: string }) {
  return <Pill tone={paymentStatusTone(status)} label={status} testId="status-pill" />;
}

export function HealthPill({ status }: { status: string | null | undefined }) {
  return <Pill tone={healthTone(status)} label={status ?? "UNKNOWN"} testId="health-pill" />;
}

export function ClassificationPill({ classification }: { classification: string }) {
  return (
    <Pill
      tone={classificationTone(classification)}
      label={classification}
      testId="classification-pill"
    />
  );
}

export function RunStatusPill({ status }: { status: string | null | undefined }) {
  return (
    <Pill tone={reconciliationStatusTone(status)} label={status ?? "UNKNOWN"} testId="run-status" />
  );
}
