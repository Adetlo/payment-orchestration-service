package io.github.adetlo.payments.observability;

import io.github.adetlo.payments.domain.OutboxStatus;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.domain.ReconciliationClassification;
import io.github.adetlo.payments.infrastructure.persistence.OutboxEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Registers and increments every business metric. Names follow DESIGN.md except where the
 * Prometheus client reserves a suffix: {@code _created} and {@code _total} are stripped by the
 * exposition format, so the creation counter is {@code payment_received_total} and the outbox
 * gauges are {@code outbox_pending} / {@code outbox_failed}.
 */
@Component
public class PaymentMetrics {

    private final Counter created;
    private final Counter completed;
    private final Counter failed;
    private final Counter rejected;
    private final Counter cancelled;
    private final Counter retry;
    private final Counter deadLetter;
    private final Counter outboxPublished;
    private final Counter reconciliationRuns;
    private final Counter idempotencyReplay;
    private final Counter idempotencyConflict;
    private final Timer processingDuration;
    private final Map<ReconciliationClassification, Counter> mismatches =
            new EnumMap<>(ReconciliationClassification.class);

    public PaymentMetrics(MeterRegistry registry, OutboxEventRepository outboxEventRepository) {
        created = counter(registry, "payment.received", "Payments accepted by the API (RECEIVED)");
        completed = counter(registry, "payment.completed", "Payments settled successfully");
        failed = counter(registry, "payment.failed", "Payments that failed permanently");
        rejected =
                counter(registry, "payment.rejected", "Payments rejected by business validation");
        cancelled = counter(registry, "payment.cancelled", "Payments cancelled by the client");
        retry = counter(registry, "payment.retry", "Settlement retries scheduled");
        deadLetter =
                counter(registry, "payment.dead.letter", "Records sent to the dead-letter topic");
        outboxPublished = counter(registry, "outbox.published", "Outbox events published to Kafka");
        reconciliationRuns =
                counter(registry, "reconciliation.runs", "Reconciliation runs executed");
        idempotencyReplay =
                counter(
                        registry,
                        "idempotency.replay",
                        "Requests served from a stored idempotent response");
        idempotencyConflict =
                counter(
                        registry,
                        "idempotency.conflict",
                        "Idempotency key reused with a different payload");
        processingDuration =
                Timer.builder("payment.processing.duration")
                        .description("Time from RECEIVED to a terminal status")
                        .publishPercentileHistogram()
                        .register(registry);
        for (ReconciliationClassification classification : ReconciliationClassification.values()) {
            if (classification != ReconciliationClassification.MATCHED) {
                mismatches.put(
                        classification,
                        Counter.builder("reconciliation.mismatch")
                                .description("Reconciliation discrepancies by classification")
                                .tag("classification", classification.name())
                                .register(registry));
            }
        }
        Gauge.builder(
                        "outbox.pending",
                        outboxEventRepository,
                        r -> r.countByStatus(OutboxStatus.NEW))
                .description("Outbox events waiting to be published")
                .register(registry);
        Gauge.builder(
                        "outbox.failed",
                        outboxEventRepository,
                        r -> r.countByStatus(OutboxStatus.FAILED))
                .description("Outbox events that exhausted their send attempts")
                .register(registry);
    }

    private static Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name).description(description).register(registry);
    }

    public void paymentCreated() {
        created.increment();
    }

    /** Counts the terminal/retry outcome of a transition and records the processing time. */
    public void transitioned(PaymentStatus target, Duration sinceCreated) {
        switch (target) {
            case COMPLETED -> completed.increment();
            case FAILED -> failed.increment();
            case REJECTED -> rejected.increment();
            case CANCELLED -> cancelled.increment();
            case RETRY_PENDING -> retry.increment();
            case RECEIVED, VALIDATED, PROCESSING -> {
                // not counted
            }
        }
        if (target.isTerminal()) {
            processingDuration.record(sinceCreated);
        }
    }

    public void deadLettered() {
        deadLetter.increment();
    }

    public void outboxPublished() {
        outboxPublished.increment();
    }

    public void reconciliationRun(Map<ReconciliationClassification, Integer> byClassification) {
        reconciliationRuns.increment();
        byClassification.forEach(
                (classification, count) -> {
                    Counter counter = mismatches.get(classification);
                    if (counter != null && count > 0) {
                        counter.increment(count);
                    }
                });
    }

    public void idempotencyReplayed() {
        idempotencyReplay.increment();
    }

    public void idempotencyConflict() {
        idempotencyConflict.increment();
    }
}
