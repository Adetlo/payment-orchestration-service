import { CLASSIFICATIONS, type Classification } from "../api/types";
import { titleCase } from "../lib/format";

interface ClassificationBarProps {
  byClassification: Partial<Record<Classification, number>>;
}

/** Stacked proportional meter of reconciliation outcomes. Counts are always shown in the legend. */
export function ClassificationBar({ byClassification }: ClassificationBarProps) {
  const entries = CLASSIFICATIONS.map((key) => ({ key, count: byClassification[key] ?? 0 }));
  const total = entries.reduce((acc, e) => acc + e.count, 0);
  return (
    <div data-testid="classification-bar">
      <div
        className="class-bar"
        role="img"
        aria-label={entries.map((e) => `${titleCase(e.key)} ${String(e.count)}`).join(", ")}
      >
        {total > 0 &&
          entries
            .filter((e) => e.count > 0)
            .map((e) => (
              <span
                key={e.key}
                className={`class-bar-segment fill-${e.key}`}
                style={{ flexGrow: e.count }}
                title={`${titleCase(e.key)}: ${String(e.count)}`}
              />
            ))}
      </div>
      <ul className="class-legend">
        {entries.map((e) => (
          <li key={e.key} data-testid={`class-${e.key}`}>
            <span className={`swatch fill-${e.key}`} aria-hidden="true" />
            {titleCase(e.key)} <strong>{e.count}</strong>
          </li>
        ))}
      </ul>
    </div>
  );
}
