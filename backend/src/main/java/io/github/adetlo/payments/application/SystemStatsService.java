package io.github.adetlo.payments.application;

import io.github.adetlo.payments.application.reconciliation.ReconciliationService;
import io.github.adetlo.payments.domain.OutboxStatus;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.domain.ReconciliationRun;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.OutboxEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aggregated counters for the dashboard; all values come from the database so they survive
 * restarts.
 */
@Service
public class SystemStatsService {

    /** Audit action written when a record is dead-lettered. */
    public static final String DEAD_LETTER_ACTION = "payment.dead-lettered";

    private final PaymentRepository paymentRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final ReconciliationService reconciliationService;

    public SystemStatsService(
            PaymentRepository paymentRepository,
            OutboxEventRepository outboxEventRepository,
            AuditEntryRepository auditEntryRepository,
            ReconciliationService reconciliationService) {
        this.paymentRepository = paymentRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.reconciliationService = reconciliationService;
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        Map<PaymentStatus, Long> byStatus = new EnumMap<>(PaymentStatus.class);
        for (PaymentStatus status : PaymentStatus.values()) {
            byStatus.put(status, 0L);
        }
        paymentRepository
                .countByStatusGrouped()
                .forEach(c -> byStatus.put(c.getStatus(), c.getCount()));
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        long published = outboxEventRepository.countByStatus(OutboxStatus.PUBLISHED);
        return new Stats(
                total,
                byStatus,
                outboxEventRepository.countByStatus(OutboxStatus.NEW),
                outboxEventRepository.countByStatus(OutboxStatus.FAILED),
                published,
                auditEntryRepository.countByAction(DEAD_LETTER_ACTION),
                reconciliationService.lastRun());
    }

    /** Snapshot of the system counters. */
    public record Stats(
            long totalPayments,
            Map<PaymentStatus, Long> byStatus,
            long outboxPending,
            long outboxFailed,
            long outboxPublished,
            long deadLetterTotal,
            Optional<ReconciliationRun> lastRun) {
        public Stats {
            byStatus = Map.copyOf(byStatus);
        }
    }
}
