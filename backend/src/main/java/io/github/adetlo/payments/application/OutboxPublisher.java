package io.github.adetlo.payments.application;

import io.github.adetlo.payments.application.event.KafkaTopics;
import io.github.adetlo.payments.application.port.EventPublisher;
import io.github.adetlo.payments.config.PaymentsProperties;
import io.github.adetlo.payments.domain.OutboxEvent;
import io.github.adetlo.payments.domain.OutboxStatus;
import io.github.adetlo.payments.infrastructure.persistence.OutboxEventRepository;
import io.github.adetlo.payments.observability.PaymentMetrics;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes NEW outbox rows to Kafka. A batch is claimed with {@code FOR UPDATE SKIP LOCKED} so
 * several instances can run concurrently; failures back off exponentially and eventually park the
 * row as FAILED.
 */
@Component
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final EventPublisher publisher;
    private final PaymentMetrics metrics;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxPublisher(
            OutboxEventRepository repository,
            EventPublisher publisher,
            PaymentMetrics metrics,
            Clock clock,
            PaymentsProperties properties) {
        this.repository = repository;
        this.publisher = publisher;
        this.metrics = metrics;
        this.clock = clock;
        this.batchSize = properties.outbox().batchSize();
        this.maxAttempts = properties.outbox().maxAttempts();
    }

    /** Publishes one batch; returns the number of rows marked PUBLISHED. */
    @Transactional
    public int publishBatch() {
        Instant now = clock.instant();
        List<OutboxEvent> batch = repository.claimBatch(now, batchSize);
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                publisher.publish(topicFor(event), event.aggregateId(), event.payload());
                event.markPublished(clock.instant());
                metrics.outboxPublished();
                published++;
            } catch (RuntimeException e) {
                event.recordFailure(e.toString(), clock.instant(), maxAttempts);
                if (event.status() == OutboxStatus.FAILED) {
                    log.error(
                            "Outbox event {} ({}) failed permanently after {} attempts: {}",
                            event.id(),
                            event.eventType(),
                            event.attempts(),
                            e.toString());
                } else {
                    log.warn(
                            "Outbox event {} ({}) send failed (attempt {}), next at {}: {}",
                            event.id(),
                            event.eventType(),
                            event.attempts(),
                            event.nextAttemptAt(),
                            e.toString());
                }
            }
        }
        repository.saveAll(batch);
        return published;
    }

    /** Topic routing by aggregate type. */
    public static String topicFor(OutboxEvent event) {
        return OutboxEvent.AGGREGATE_RECONCILIATION.equals(event.aggregateType())
                ? KafkaTopics.RECONCILIATION_EVENTS
                : KafkaTopics.PAYMENT_EVENTS;
    }
}
