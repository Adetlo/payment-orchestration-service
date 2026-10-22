package io.github.adetlo.payments.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** Body of {@code GET /api/v1/system/stats}. */
public record SystemStatsResponse(
        Payments payments, Outbox outbox, Kafka kafka, Reconciliation reconciliation) {

    /** Payment counts. */
    public record Payments(long total, Map<String, Long> byStatus) {
        public Payments {
            byStatus = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(byStatus));
        }
    }

    /** Outbox counts. */
    public record Outbox(long pending, long failed, long published) {}

    /** Kafka counts. */
    public record Kafka(long publishedTotal, long deadLetterTotal) {}

    /** Last reconciliation run, if any. */
    public record Reconciliation(ReconciliationRunResponse lastRun) {}
}
