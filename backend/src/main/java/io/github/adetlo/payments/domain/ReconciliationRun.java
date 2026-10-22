package io.github.adetlo.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/** A reconciliation run with its aggregate counts per classification. */
@Entity
@Table(name = "reconciliation_runs")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "run_id", nullable = false, unique = true)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scenario", nullable = false, length = 32)
    private ReconciliationScenario scenario;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private ReconciliationStatus status;

    @Column(name = "seed", nullable = false)
    private long seed;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "records_checked", nullable = false)
    private int recordsChecked;

    @Column(name = "matched", nullable = false)
    private int matched;

    @Column(name = "unmatched", nullable = false)
    private int unmatched;

    @Column(name = "amount_mismatch_count", nullable = false)
    private int amountMismatchCount;

    @Column(name = "missing_internal_count", nullable = false)
    private int missingInternalCount;

    @Column(name = "missing_external_count", nullable = false)
    private int missingExternalCount;

    @Column(name = "status_mismatch_count", nullable = false)
    private int statusMismatchCount;

    protected ReconciliationRun() {
        // JPA
    }

    public static ReconciliationRun start(ReconciliationScenario scenario, long seed, Instant now) {
        ReconciliationRun run = new ReconciliationRun();
        run.runId = UUID.randomUUID();
        run.scenario = scenario;
        run.status = ReconciliationStatus.RUNNING;
        run.seed = seed;
        run.startedAt = now;
        return run;
    }

    /** Completes the run, deriving the counts from the classified items. */
    public void complete(Collection<ReconciliationItem> items, Instant now) {
        Map<ReconciliationClassification, Integer> counts =
                new EnumMap<>(ReconciliationClassification.class);
        for (ReconciliationItem item : items) {
            counts.merge(item.classification(), 1, Integer::sum);
        }
        recordsChecked = items.size();
        matched = counts.getOrDefault(ReconciliationClassification.MATCHED, 0);
        amountMismatchCount = counts.getOrDefault(ReconciliationClassification.AMOUNT_MISMATCH, 0);
        missingInternalCount =
                counts.getOrDefault(ReconciliationClassification.MISSING_INTERNAL, 0);
        missingExternalCount =
                counts.getOrDefault(ReconciliationClassification.MISSING_EXTERNAL, 0);
        statusMismatchCount = counts.getOrDefault(ReconciliationClassification.STATUS_MISMATCH, 0);
        unmatched = recordsChecked - matched;
        status = ReconciliationStatus.COMPLETED;
        completedAt = now;
    }

    public void fail(Instant now) {
        status = ReconciliationStatus.FAILED;
        completedAt = now;
    }

    /** Counts keyed by classification, always containing every classification. */
    public Map<ReconciliationClassification, Integer> byClassification() {
        Map<ReconciliationClassification, Integer> counts =
                new EnumMap<>(ReconciliationClassification.class);
        counts.put(ReconciliationClassification.MATCHED, matched);
        counts.put(ReconciliationClassification.AMOUNT_MISMATCH, amountMismatchCount);
        counts.put(ReconciliationClassification.MISSING_INTERNAL, missingInternalCount);
        counts.put(ReconciliationClassification.MISSING_EXTERNAL, missingExternalCount);
        counts.put(ReconciliationClassification.STATUS_MISMATCH, statusMismatchCount);
        return counts;
    }

    public Long id() {
        return id;
    }

    public UUID runId() {
        return runId;
    }

    public ReconciliationScenario scenario() {
        return scenario;
    }

    public ReconciliationStatus status() {
        return status;
    }

    public long seed() {
        return seed;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant completedAt() {
        return completedAt;
    }

    public int recordsChecked() {
        return recordsChecked;
    }

    public int matched() {
        return matched;
    }

    public int unmatched() {
        return unmatched;
    }
}
