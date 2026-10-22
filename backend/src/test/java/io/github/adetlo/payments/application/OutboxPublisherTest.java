package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adetlo.payments.application.event.KafkaTopics;
import io.github.adetlo.payments.application.port.EventPublisher;
import io.github.adetlo.payments.domain.OutboxEvent;
import io.github.adetlo.payments.domain.OutboxStatus;
import io.github.adetlo.payments.infrastructure.persistence.OutboxEventRepository;
import io.github.adetlo.payments.observability.PaymentMetrics;
import io.github.adetlo.payments.support.TestProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    private final OutboxEventRepository repository = mock(OutboxEventRepository.class);
    private final EventPublisher publisher = mock(EventPublisher.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher =
                new OutboxPublisher(
                        repository,
                        publisher,
                        new PaymentMetrics(registry, repository),
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        TestProperties.defaults());
    }

    @Test
    void publishesClaimedBatchAndMarksRowsPublished() {
        OutboxEvent payment =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_PAYMENT,
                        "p-1",
                        "PaymentReceived",
                        1,
                        "{\"a\":1}",
                        NOW);
        OutboxEvent reconciliation =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_RECONCILIATION,
                        "r-1",
                        "ReconciliationCompleted",
                        1,
                        "{}",
                        NOW);
        when(repository.claimBatch(NOW, 50)).thenReturn(List.of(payment, reconciliation));

        int published = outboxPublisher.publishBatch();

        assertThat(published).isEqualTo(2);
        verify(publisher).publish(KafkaTopics.PAYMENT_EVENTS, "p-1", "{\"a\":1}");
        verify(publisher).publish(KafkaTopics.RECONCILIATION_EVENTS, "r-1", "{}");
        assertThat(payment.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(payment.publishedAt()).isEqualTo(NOW);
        assertThat(reconciliation.status()).isEqualTo(OutboxStatus.PUBLISHED);
        verify(repository).saveAll(List.of(payment, reconciliation));
        assertThat(registry.get("outbox.published").counter().count()).isEqualTo(2.0);
    }

    @Test
    void sendFailureSchedulesRetryWithBackoff() {
        OutboxEvent event =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_PAYMENT, "p-1", "PaymentReceived", 1, "{}", NOW);
        when(repository.claimBatch(any(), anyInt())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("broker down"))
                .when(publisher)
                .publish(anyString(), anyString(), anyString());

        int published = outboxPublisher.publishBatch();

        assertThat(published).isZero();
        assertThat(event.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(event.attempts()).isEqualTo(1);
        assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.lastError()).contains("broker down");
        verify(repository).saveAll(List.of(event));
    }

    @Test
    void repeatedFailuresFollowBackoffTableThenFail() {
        OutboxEvent event =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_PAYMENT, "p-1", "PaymentReceived", 1, "{}", NOW);
        when(repository.claimBatch(any(), anyInt())).thenReturn(List.of(event));
        doThrow(new IllegalStateException("down"))
                .when(publisher)
                .publish(anyString(), anyString(), anyString());
        long[] backoffs = {1, 2, 4, 8, 16};

        for (long backoff : backoffs) {
            outboxPublisher.publishBatch();
            assertThat(event.status()).isEqualTo(OutboxStatus.NEW);
            assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(backoff));
        }
        outboxPublisher.publishBatch();

        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.attempts()).isEqualTo(6);
        assertThat(registry.get("outbox.published").counter().count()).isZero();
    }

    @Test
    void oneBadRowDoesNotBlockTheOthers() {
        OutboxEvent bad =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_PAYMENT, "bad", "PaymentReceived", 1, "{}", NOW);
        OutboxEvent good =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_PAYMENT, "good", "PaymentReceived", 1, "{}", NOW);
        when(repository.claimBatch(any(), anyInt())).thenReturn(List.of(bad, good));
        doThrow(new IllegalStateException("x"))
                .when(publisher)
                .publish(anyString(), eq("bad"), anyString());

        assertThat(outboxPublisher.publishBatch()).isEqualTo(1);
        assertThat(bad.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(good.status()).isEqualTo(OutboxStatus.PUBLISHED);
    }

    @Test
    void emptyBatchPublishesNothing() {
        when(repository.claimBatch(any(), anyInt())).thenReturn(List.of());

        assertThat(outboxPublisher.publishBatch()).isZero();
        verify(repository).saveAll(List.of());
    }
}
