import { useMemo } from "react";
import type { PaymentEventResponse } from "../api/types";
import { formatInstant, stringifyDetails } from "../lib/format";
import { paymentStatusTone } from "../lib/status";

/** Oldest first; ties broken by eventVersion then original order. */
export function sortEventsOldestFirst(events: PaymentEventResponse[]): PaymentEventResponse[] {
  return events
    .map((event, index) => ({ event, index }))
    .sort((a, b) => {
      const ta = Date.parse(a.event.occurredAt);
      const tb = Date.parse(b.event.occurredAt);
      if (ta !== tb) return ta - tb;
      return a.index - b.index;
    })
    .map(({ event }) => event);
}

export function Timeline({ events }: { events: PaymentEventResponse[] }) {
  const ordered = useMemo(() => sortEventsOldestFirst(events), [events]);
  if (ordered.length === 0) {
    return <div className="table-empty">No events recorded yet.</div>;
  }
  return (
    <ol className="timeline">
      {ordered.map((event) => {
        const tone = event.toStatus ? paymentStatusTone(event.toStatus) : "grey";
        const details = stringifyDetails(event.details);
        return (
          <li key={event.eventId} className="timeline-item" data-testid="timeline-event">
            <span className={`timeline-dot tone-${tone}`} aria-hidden="true" />
            <div className="timeline-head">
              <span className="timeline-type">{event.eventType}</span>
              <span className="timeline-transition">
                {event.fromStatus ?? "∅"} → {event.toStatus ?? "∅"}
              </span>
              <span className="timeline-time">{formatInstant(event.occurredAt)}</span>
            </div>
            {details && <pre className="timeline-details">{details}</pre>}
          </li>
        );
      })}
    </ol>
  );
}
