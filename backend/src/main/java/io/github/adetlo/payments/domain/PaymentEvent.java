package io.github.adetlo.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An immutable record of one status transition of a payment. */
@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    public static final int CURRENT_VERSION = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 64)
    private PaymentEventType eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 32)
    private PaymentStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 32)
    private PaymentStatus toStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String details;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    protected PaymentEvent() {
        // JPA
    }

    public static PaymentEvent of(
            UUID eventId,
            UUID paymentId,
            PaymentEventType eventType,
            PaymentStatus fromStatus,
            PaymentStatus toStatus,
            String detailsJson,
            String correlationId,
            Instant occurredAt) {
        PaymentEvent event = new PaymentEvent();
        event.eventId = eventId;
        event.paymentId = paymentId;
        event.eventType = eventType;
        event.eventVersion = CURRENT_VERSION;
        event.occurredAt = occurredAt;
        event.fromStatus = fromStatus;
        event.toStatus = toStatus;
        event.details = detailsJson;
        event.correlationId = correlationId;
        return event;
    }

    public Long id() {
        return id;
    }

    public UUID eventId() {
        return eventId;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public PaymentEventType eventType() {
        return eventType;
    }

    public int eventVersion() {
        return eventVersion;
    }

    public Instant occurredAt() {
        return occurredAt;
    }

    public PaymentStatus fromStatus() {
        return fromStatus;
    }

    public PaymentStatus toStatus() {
        return toStatus;
    }

    public String details() {
        return details;
    }

    public String correlationId() {
        return correlationId;
    }
}
