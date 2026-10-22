package io.github.adetlo.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The payment aggregate. Status changes go through {@link #transitionTo(PaymentStatus, Instant,
 * String)}, which enforces the transition table in {@link PaymentStatus}; services wrap that call
 * to record events, audit entries and outbox messages in the same transaction.
 */
@Entity
@Table(name = "payments")
public class Payment {

    /** Longest wait between two settlement attempts. */
    public static final Duration MAX_RETRY_BACKOFF = Duration.ofSeconds(30);

    @Id
    @Column(name = "payment_id", nullable = false)
    private UUID paymentId;

    @Column(name = "payer_account", nullable = false, length = 34)
    private String payerAccount;

    @Column(name = "payee_account", nullable = false, length = 34)
    private String payeeAccount;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "reference", nullable = false, length = 35)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private PaymentStatus status;

    @Column(name = "failure_reason", length = 512)
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "demo_failure_mode", length = 32)
    private DemoFailureMode demoFailureMode;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "correlation_id", nullable = false, length = 64)
    private String correlationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Payment() {
        // JPA
    }

    /** Creates a new payment in {@link PaymentStatus#RECEIVED}. */
    public static Payment create(
            PaymentInstruction instruction,
            String idempotencyKey,
            String correlationId,
            DemoFailureMode demoFailureMode,
            int maxRetries,
            Instant now) {
        Objects.requireNonNull(instruction, "instruction");
        Payment payment = new Payment();
        payment.paymentId = UUID.randomUUID();
        payment.payerAccount = instruction.payerAccount().value();
        payment.payeeAccount = instruction.payeeAccount().value();
        payment.amount = instruction.money().amount();
        payment.currency = instruction.money().currencyCode();
        payment.reference = instruction.reference();
        payment.status = PaymentStatus.RECEIVED;
        payment.retryCount = 0;
        payment.maxRetries = maxRetries;
        payment.demoFailureMode = demoFailureMode;
        payment.idempotencyKey = idempotencyKey;
        payment.correlationId = Objects.requireNonNull(correlationId, "correlationId");
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
    }

    /**
     * Moves the payment to {@code target}, applying the per-status side effects (retry bookkeeping,
     * failure reason). Throws {@link InvalidStateTransitionException} when the table forbids it.
     */
    public void transitionTo(PaymentStatus target, Instant now, String reason) {
        if (!status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException(paymentId, status, target);
        }
        switch (target) {
            case RETRY_PENDING -> {
                nextRetryAt = now.plus(backoffFor(retryCount));
                retryCount++;
                failureReason = reason;
            }
            case PROCESSING -> nextRetryAt = null;
            case COMPLETED -> {
                nextRetryAt = null;
                failureReason = null;
            }
            case FAILED, REJECTED, CANCELLED -> {
                nextRetryAt = null;
                failureReason = reason;
            }
            case VALIDATED, RECEIVED -> {
                // no side effects
            }
        }
        status = target;
        updatedAt = now;
    }

    /** Exponential backoff {@code 2^attempt} seconds, capped at {@link #MAX_RETRY_BACKOFF}. */
    public static Duration backoffFor(int attempt) {
        long seconds = 1L << Math.min(attempt, 30);
        Duration backoff = Duration.ofSeconds(seconds);
        return backoff.compareTo(MAX_RETRY_BACKOFF) > 0 ? MAX_RETRY_BACKOFF : backoff;
    }

    public boolean retriesExhausted() {
        return retryCount >= maxRetries;
    }

    public boolean isRetryDue(Instant now) {
        return status == PaymentStatus.RETRY_PENDING
                && nextRetryAt != null
                && !nextRetryAt.isAfter(now);
    }

    public PaymentInstruction instruction() {
        return new PaymentInstruction(
                new AccountReference(payerAccount),
                new AccountReference(payeeAccount),
                money(),
                reference);
    }

    public Money money() {
        return new Money(amount, Currency.getInstance(currency));
    }

    public UUID paymentId() {
        return paymentId;
    }

    public PaymentStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }

    public int retryCount() {
        return retryCount;
    }

    public int maxRetries() {
        return maxRetries;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public DemoFailureMode demoFailureMode() {
        return demoFailureMode;
    }

    public String idempotencyKey() {
        return idempotencyKey;
    }

    public String correlationId() {
        return correlationId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Long version() {
        return version;
    }
}
