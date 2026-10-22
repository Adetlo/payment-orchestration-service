package io.github.adetlo.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Stored outcome of a request made with an {@code Idempotency-Key}. Rows are inserted with a
 * conflict-free native INSERT (see the repository) so the primary key is the concurrency guard. The
 * response body is kept as text so a replay returns exactly the bytes the first call did.
 */
@Entity
@Table(name = "idempotency_records")
public class IdempotencyRecord {

    @Id
    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "response_status")
    private Integer responseStatus;

    @Column(name = "response_body", columnDefinition = "text")
    private String responseBody;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private IdempotencyState state;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected IdempotencyRecord() {
        // JPA
    }

    public void complete(UUID paymentId, int responseStatus, String responseBody) {
        this.paymentId = paymentId;
        this.responseStatus = responseStatus;
        this.responseBody = responseBody;
        this.state = IdempotencyState.COMPLETED;
    }

    public boolean matches(String otherRequestHash) {
        return requestHash.equals(otherRequestHash);
    }

    public boolean isInProgress() {
        return state == IdempotencyState.IN_PROGRESS;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String requestHash() {
        return requestHash;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public Integer responseStatus() {
        return responseStatus;
    }

    public String responseBody() {
        return responseBody;
    }

    public IdempotencyState state() {
        return state;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant expiresAt() {
        return expiresAt;
    }
}
