package io.github.adetlo.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class OutboxEventTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Test
    void newEventIsPublishableImmediately() {
        OutboxEvent event =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_PAYMENT, "p-1", "PaymentReceived", 1, "{}", NOW);

        assertThat(event.status()).isEqualTo(OutboxStatus.NEW);
        assertThat(event.attempts()).isZero();
        assertThat(event.nextAttemptAt()).isEqualTo(NOW);
        assertThat(event.publishedAt()).isNull();

        event.markPublished(NOW.plusSeconds(1));
        assertThat(event.status()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.publishedAt()).isEqualTo(NOW.plusSeconds(1));
    }

    @Test
    void failuresBackOffExponentiallyThenParkTheRow() {
        OutboxEvent event =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_PAYMENT, "p-1", "PaymentReceived", 1, "{}", NOW);
        long[] expectedBackoffSeconds = {1, 2, 4, 8, 16};

        for (int i = 0; i < expectedBackoffSeconds.length; i++) {
            event.recordFailure("boom " + i, NOW, 5);
            assertThat(event.status()).isEqualTo(OutboxStatus.NEW);
            assertThat(event.attempts()).isEqualTo(i + 1);
            assertThat(event.nextAttemptAt()).isEqualTo(NOW.plusSeconds(expectedBackoffSeconds[i]));
            assertThat(event.lastError()).isEqualTo("boom " + i);
        }

        event.recordFailure("final", NOW, 5);
        assertThat(event.status()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.attempts()).isEqualTo(6);
    }

    @Test
    void backoffTableAndErrorTruncation() {
        assertThat(OutboxEvent.backoffFor(1)).isEqualTo(Duration.ofSeconds(1));
        assertThat(OutboxEvent.backoffFor(5)).isEqualTo(Duration.ofSeconds(16));
        OutboxEvent event =
                OutboxEvent.of(
                        OutboxEvent.AGGREGATE_RECONCILIATION,
                        "r-1",
                        "ReconciliationCompleted",
                        1,
                        "{}",
                        NOW);

        event.recordFailure("x".repeat(5000), NOW, 5);
        assertThat(event.lastError()).hasSize(1024);
        event.recordFailure(null, NOW, 5);
        assertThat(event.lastError()).isEqualTo("unknown");
    }
}
