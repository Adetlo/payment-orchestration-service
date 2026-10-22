package io.github.adetlo.payments.application.reconciliation;

import io.github.adetlo.payments.application.ReconciliationRunNotFoundException;
import io.github.adetlo.payments.application.event.EventEnvelope;
import io.github.adetlo.payments.application.event.EventEnvelopeFactory;
import io.github.adetlo.payments.application.port.SettlementFeed;
import io.github.adetlo.payments.application.port.SettlementFeedRecord;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.AuditEntry;
import io.github.adetlo.payments.domain.OutboxEvent;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentEvent;
import io.github.adetlo.payments.domain.PaymentEventType;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.domain.ReconciliationItem;
import io.github.adetlo.payments.domain.ReconciliationRun;
import io.github.adetlo.payments.domain.ReconciliationScenario;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.OutboxEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.infrastructure.persistence.ReconciliationItemRepository;
import io.github.adetlo.payments.infrastructure.persistence.ReconciliationRunRepository;
import io.github.adetlo.payments.observability.PaymentMetrics;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Runs reconciliation of terminal payments against the external settlement feed. */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
    public static final long DEFAULT_SEED = 42L;

    private final PaymentRepository paymentRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationItemRepository itemRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SettlementFeed settlementFeed;
    private final EventEnvelopeFactory envelopeFactory;
    private final PaymentMetrics metrics;
    private final Clock clock;

    public ReconciliationService(
            PaymentRepository paymentRepository,
            ReconciliationRunRepository runRepository,
            ReconciliationItemRepository itemRepository,
            AuditEntryRepository auditEntryRepository,
            OutboxEventRepository outboxEventRepository,
            SettlementFeed settlementFeed,
            EventEnvelopeFactory envelopeFactory,
            PaymentMetrics metrics,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.settlementFeed = settlementFeed;
        this.envelopeFactory = envelopeFactory;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Executes a run synchronously and returns it together with its items. */
    @Transactional
    public ReconciliationResult run(ReconciliationScenario scenario, Long seed) {
        long effectiveSeed = seed == null ? DEFAULT_SEED : seed;
        Instant now = clock.instant();
        ReconciliationRun run =
                runRepository.save(ReconciliationRun.start(scenario, effectiveSeed, now));
        List<Payment> internal =
                paymentRepository.findByStatusInOrderByCreatedAtAscPaymentIdAsc(
                        List.of(PaymentStatus.COMPLETED, PaymentStatus.FAILED));
        List<SettlementFeedRecord> external =
                settlementFeed.fetch(internal, scenario, effectiveSeed);
        List<ReconciliationItem> items =
                itemRepository.saveAll(
                        ReconciliationMatcher.match(run.runId(), internal, external));
        run.complete(items, clock.instant());
        ReconciliationRun saved = runRepository.save(run);
        Map<String, Object> data = summary(saved);
        auditEntryRepository.save(
                AuditEntry.of(
                        null,
                        "reconciliation.completed",
                        AuditActor.API,
                        envelopeFactory.toJson(data),
                        saved.completedAt()));
        EventEnvelope envelope =
                new EventEnvelope(
                        UUID.randomUUID(),
                        PaymentEventType.ReconciliationCompleted.name(),
                        PaymentEvent.CURRENT_VERSION,
                        saved.completedAt(),
                        null,
                        saved.runId().toString(),
                        data);
        outboxEventRepository.save(
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_RECONCILIATION,
                        saved.runId().toString(),
                        PaymentEventType.ReconciliationCompleted.name(),
                        PaymentEvent.CURRENT_VERSION,
                        envelopeFactory.toJson(envelope),
                        saved.completedAt()));
        metrics.reconciliationRun(saved.byClassification());
        log.info(
                "Reconciliation run {} ({}) checked {} records, {} unmatched",
                saved.runId(),
                scenario.wireName(),
                saved.recordsChecked(),
                saved.unmatched());
        return new ReconciliationResult(saved, items);
    }

    @Transactional(readOnly = true)
    public ReconciliationResult get(UUID runId) {
        ReconciliationRun run =
                runRepository
                        .findByRunId(runId)
                        .orElseThrow(() -> new ReconciliationRunNotFoundException(runId));
        return new ReconciliationResult(run, itemRepository.findByRunIdOrderByIdAsc(runId));
    }

    @Transactional(readOnly = true)
    public Page<ReconciliationRun> list(int page, int size) {
        return runRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startedAt", "id")));
    }

    @Transactional(readOnly = true)
    public Optional<ReconciliationRun> lastRun() {
        return runRepository.findFirstByOrderByStartedAtDescIdDesc();
    }

    private static Map<String, Object> summary(ReconciliationRun run) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("runId", run.runId().toString());
        data.put("scenario", run.scenario().wireName());
        data.put("status", run.status().name());
        data.put("recordsChecked", run.recordsChecked());
        data.put("matched", run.matched());
        data.put("unmatched", run.unmatched());
        Map<String, Integer> byClassification = new LinkedHashMap<>();
        run.byClassification().forEach((k, v) -> byClassification.put(k.name(), v));
        data.put("byClassification", byClassification);
        return data;
    }

    /** A run and its items. */
    public record ReconciliationResult(ReconciliationRun run, List<ReconciliationItem> items) {
        public ReconciliationResult {
            items = List.copyOf(items);
        }
    }
}
