package io.github.adetlo.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Transactional outbox row: an event envelope written in the same transaction as the state change
 * it describes and published to Kafka asynchronously by the outbox publisher.
 */
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public static final String AGGREGATE_PAYMENT = "Payment";
    public static final String AGGREGATE_RECONCILIATION = "Reconciliation";
    private static final int MAX_ERROR_LENGTH = 1024;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private OutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = MAX_ERROR_LENGTH)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA
    }

    public static OutboxEvent of(
            String aggregateType,
            String aggregateId,
            String eventType,
            int eventVersion,
            String payload,
            Instant now) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.eventVersion = eventVersion;
        event.payload = payload;
        event.status = OutboxStatus.NEW;
        event.attempts = 0;
        event.nextAttemptAt = now;
        event.createdAt = now;
        return event;
    }

    public void markPublished(Instant now) {
        status = OutboxStatus.PUBLISHED;
        publishedAt = now;
        lastError = null;
    }

    /**
     * Records a failed send: schedules the next attempt with exponential backoff (1s, 2s, 4s, ...)
     * or, once {@code maxAttempts} retries are used up, marks the row FAILED.
     */
    public void recordFailure(String error, Instant now, int maxAttempts) {
        attempts++;
        lastError =
                error == null
                        ? "unknown"
                        : error.substring(0, Math.min(error.length(), MAX_ERROR_LENGTH));
        if (attempts > maxAttempts) {
            status = OutboxStatus.FAILED;
        } else {
            nextAttemptAt = now.plus(backoffFor(attempts));
        }
    }

    /** Backoff before retry number {@code attempt} (1-based): 1s, 2s, 4s, 8s, 16s... */
    public static Duration backoffFor(int attempt) {
        return Duration.ofSeconds(1L << Math.min(Math.max(attempt - 1, 0), 30));
    }

    public Long id() {
        return id;
    }

    public String aggregateType() {
        return aggregateType;
    }

    public String aggregateId() {
        return aggregateId;
    }

    public String eventType() {
        return eventType;
    }

    public int eventVersion() {
        return eventVersion;
    }

    public String payload() {
        return payload;
    }

    public OutboxStatus status() {
        return status;
    }

    public int attempts() {
        return attempts;
    }

    public Instant nextAttemptAt() {
        return nextAttemptAt;
    }

    public String lastError() {
        return lastError;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant publishedAt() {
        return publishedAt;
    }
}
