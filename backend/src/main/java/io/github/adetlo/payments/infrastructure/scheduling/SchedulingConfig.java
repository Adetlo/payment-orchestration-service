package io.github.adetlo.payments.infrastructure.scheduling;

import io.github.adetlo.payments.application.OutboxPublisher;
import io.github.adetlo.payments.application.PaymentProcessingService;
import io.github.adetlo.payments.application.SettlementRunner;
import io.github.adetlo.payments.application.idempotency.IdempotencyService;
import io.github.adetlo.payments.config.PaymentsProperties;
import io.github.adetlo.payments.domain.AuditActor;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

/**
 * Registers the background jobs with the intervals from {@link PaymentsProperties}: outbox
 * publishing, retry scheduling and idempotency cleanup. Every job swallows and logs its own
 * failures so one bad tick never stops the schedule.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(SchedulingConfig.class);

    private final OutboxPublisher outboxPublisher;
    private final PaymentProcessingService processingService;
    private final SettlementRunner settlementRunner;
    private final IdempotencyService idempotencyService;
    private final PaymentsProperties properties;

    public SchedulingConfig(
            OutboxPublisher outboxPublisher,
            PaymentProcessingService processingService,
            SettlementRunner settlementRunner,
            IdempotencyService idempotencyService,
            PaymentsProperties properties) {
        this.outboxPublisher = outboxPublisher;
        this.processingService = processingService;
        this.settlementRunner = settlementRunner;
        this.idempotencyService = idempotencyService;
        this.properties = properties;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addFixedDelayTask(
                guarded("outbox-publisher", this::publishOutbox),
                properties.outbox().pollInterval());
        registrar.addFixedDelayTask(
                guarded("retry-scheduler", this::runDueRetries), properties.retry().pollInterval());
        registrar.addCronTask(
                guarded("idempotency-cleanup", idempotencyService::purgeExpired),
                properties.idempotency().cleanupCron());
    }

    /** One outbox tick; drains until a batch comes back short so bursts are flushed quickly. */
    void publishOutbox() {
        int published;
        do {
            published = outboxPublisher.publishBatch();
        } while (published >= properties.outbox().batchSize());
    }

    /** One retry tick: claims due payments, then settles each outside the claiming transaction. */
    void runDueRetries() {
        List<UUID> claimed = processingService.claimDueRetries(properties.retry().batchSize());
        for (UUID paymentId : claimed) {
            settlementRunner.settle(paymentId, AuditActor.SCHEDULER);
        }
    }

    private static Runnable guarded(String name, Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("Scheduled job {} failed: {}", name, e.toString());
            }
        };
    }
}
