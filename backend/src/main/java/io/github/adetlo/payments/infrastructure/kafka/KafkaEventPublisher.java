package io.github.adetlo.payments.infrastructure.kafka;

import io.github.adetlo.payments.application.port.EventPublisher;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Synchronous Kafka adapter for the outbox publisher. */
@Component
public class KafkaEventPublisher implements EventPublisher {

    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final KafkaTemplate<String, String> kafkaTemplate;

    public KafkaEventPublisher(KafkaTemplate<String, String> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public void publish(String topic, String key, String payload) {
        try {
            kafkaTemplate
                    .send(topic, key, payload)
                    .get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaPublishException("Interrupted while publishing to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new KafkaPublishException(
                    "Publishing to " + topic + " failed: " + e.getMessage(), e);
        }
    }

    /** Raised when the broker did not acknowledge a record in time. */
    public static class KafkaPublishException extends RuntimeException {
        public KafkaPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
