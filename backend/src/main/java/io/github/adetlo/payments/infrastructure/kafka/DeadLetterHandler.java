package io.github.adetlo.payments.infrastructure.kafka;

import io.github.adetlo.payments.application.PaymentStateMachine;
import io.github.adetlo.payments.application.SystemStatsService;
import io.github.adetlo.payments.application.TransitionContext;
import io.github.adetlo.payments.application.event.EventEnvelopeFactory;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.AuditEntry;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.observability.PaymentMetrics;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bookkeeping after a record was dead-lettered: counts it, writes an audit entry and parks the
 * payment as REJECTED (the only terminal status reachable from RECEIVED) with an explicit reason,
 * so nothing is left silently stuck.
 */
@Component
public class DeadLetterHandler {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterHandler.class);

    private final PaymentRepository paymentRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final PaymentStateMachine stateMachine;
    private final EventEnvelopeFactory envelopeFactory;
    private final PaymentMetrics metrics;
    private final Clock clock;

    public DeadLetterHandler(
            PaymentRepository paymentRepository,
            AuditEntryRepository auditEntryRepository,
            PaymentStateMachine stateMachine,
            EventEnvelopeFactory envelopeFactory,
            PaymentMetrics metrics,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.stateMachine = stateMachine;
        this.envelopeFactory = envelopeFactory;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional
    public void onDeadLetter(ConsumerRecord<?, ?> record, Exception failure) {
        Throwable error = NestedExceptionUtils.getMostSpecificCause(failure);
        metrics.deadLettered();
        UUID paymentId = parsePaymentId(record.key());
        log.error(
                "Dead-lettered record from {}-{}@{} for payment {}: {}",
                record.topic(),
                record.partition(),
                record.offset(),
                paymentId,
                error.toString());
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("topic", record.topic());
        details.put("partition", record.partition());
        details.put("offset", record.offset());
        details.put("error", error.getClass().getSimpleName() + ": " + error.getMessage());
        auditEntryRepository.save(
                AuditEntry.of(
                        paymentId,
                        SystemStatsService.DEAD_LETTER_ACTION,
                        AuditActor.CONSUMER,
                        envelopeFactory.toJson(details),
                        clock.instant()));
        if (paymentId != null) {
            paymentRepository
                    .findById(paymentId)
                    .filter(p -> p.status() == PaymentStatus.RECEIVED)
                    .ifPresent(
                            p ->
                                    stateMachine.transition(
                                            p,
                                            PaymentStatus.REJECTED,
                                            TransitionContext.of(
                                                    AuditActor.CONSUMER,
                                                    "dead-lettered after repeated delivery"
                                                            + " failures: "
                                                            + error.getClass().getSimpleName())));
        }
    }

    private static UUID parsePaymentId(Object key) {
        if (key == null) {
            return null;
        }
        try {
            return UUID.fromString(key.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
