package io.github.adetlo.payments.application.port;

/** Port used by the outbox publisher to hand an envelope to the message broker. */
public interface EventPublisher {

    /**
     * Publishes synchronously; implementations must throw when the broker did not acknowledge the
     * record so the outbox can schedule a retry.
     */
    void publish(String topic, String key, String payload);
}
