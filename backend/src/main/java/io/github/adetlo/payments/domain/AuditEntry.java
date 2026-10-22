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

/**
 * Append-only audit trail entry. There is no setter and the repository exposes no update/delete; a
 * database trigger additionally rejects UPDATE and DELETE statements.
 */
@Entity
@Table(name = "audit_entries")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor", nullable = false, length = 16)
    private AuditActor actor;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false)
    private String details;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEntry() {
        // JPA
    }

    public static AuditEntry of(
            UUID paymentId,
            String action,
            AuditActor actor,
            String detailsJson,
            Instant occurredAt) {
        AuditEntry entry = new AuditEntry();
        entry.paymentId = paymentId;
        entry.action = action;
        entry.actor = actor;
        entry.details = detailsJson;
        entry.occurredAt = occurredAt;
        return entry;
    }

    public Long id() {
        return id;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public String action() {
        return action;
    }

    public AuditActor actor() {
        return actor;
    }

    public String details() {
        return details;
    }

    public Instant occurredAt() {
        return occurredAt;
    }
}
