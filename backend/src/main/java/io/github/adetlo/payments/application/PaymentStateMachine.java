package io.github.adetlo.payments.application;

import io.github.adetlo.payments.application.event.EventEnvelopeFactory;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.AuditEntry;
import io.github.adetlo.payments.domain.InvalidStateTransitionException;
import io.github.adetlo.payments.domain.OutboxEvent;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentEvent;
import io.github.adetlo.payments.domain.PaymentEventType;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.OutboxEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.observability.PaymentMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The single place where a payment changes status. Each transition validates the table, mutates the
 * aggregate and writes the history event, the audit entry and, for externally visible events, the
 * outbox row, all inside the caller's transaction.
 */
@Component
public class PaymentStateMachine {

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final EventEnvelopeFactory envelopeFactory;
    private final PaymentMetrics metrics;
    private final Clock clock;

    public PaymentStateMachine(
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            AuditEntryRepository auditEntryRepository,
            OutboxEventRepository outboxEventRepository,
            EventEnvelopeFactory envelopeFactory,
            PaymentMetrics metrics,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.envelopeFactory = envelopeFactory;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** Pure table check, usable without a Spring context. */
    public static void assertAllowed(UUID paymentId, PaymentStatus from, PaymentStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new InvalidStateTransitionException(paymentId, from, to);
        }
    }

    /** Persists a freshly created payment together with its {@code PaymentReceived} event. */
    public Payment start(Payment payment, TransitionContext context) {
        Instant now = clock.instant();
        Payment saved = paymentRepository.save(payment);
        record(saved, null, now, context, "payment.created");
        metrics.paymentCreated();
        return saved;
    }

    /** Moves the payment to {@code target} or throws {@link InvalidStateTransitionException}. */
    public Payment transition(Payment payment, PaymentStatus target, TransitionContext context) {
        PaymentStatus from = payment.status();
        assertAllowed(payment.paymentId(), from, target);
        Instant now = clock.instant();
        payment.transitionTo(target, now, context.reason());
        Payment saved = paymentRepository.save(payment);
        record(saved, from, now, context, "payment." + target.name().toLowerCase(Locale.ROOT));
        metrics.transitioned(target, Duration.between(saved.createdAt(), now));
        return saved;
    }

    private void record(
            Payment payment,
            PaymentStatus from,
            Instant now,
            TransitionContext context,
            String action) {
        PaymentEventType eventType = PaymentEventType.forTarget(payment.status());
        Map<String, Object> data =
                envelopeFactory.paymentData(
                        payment, from, context.reason(), context.extraDetails());
        UUID eventId = UUID.randomUUID();
        String dataJson = envelopeFactory.toJson(data);
        paymentEventRepository.save(
                PaymentEvent.of(
                        eventId,
                        payment.paymentId(),
                        eventType,
                        from,
                        payment.status(),
                        dataJson,
                        payment.correlationId(),
                        now));
        auditEntryRepository.save(
                AuditEntry.of(
                        payment.paymentId(),
                        action,
                        context.actor(),
                        auditDetails(data, context.actor(), eventId),
                        now));
        if (eventType.isPublished()) {
            String payload =
                    envelopeFactory.toJson(
                            envelopeFactory.envelope(
                                    eventId,
                                    eventType,
                                    payment.paymentId(),
                                    payment.correlationId(),
                                    now,
                                    data));
            outboxEventRepository.save(
                    OutboxEvent.of(
                            OutboxEvent.AGGREGATE_PAYMENT,
                            payment.paymentId().toString(),
                            eventType.name(),
                            PaymentEvent.CURRENT_VERSION,
                            payload,
                            now));
        }
    }

    private String auditDetails(Map<String, Object> data, AuditActor actor, UUID eventId) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("eventId", eventId.toString());
        details.put("actor", actor.wireName());
        details.put("status", data.get("status"));
        details.put("fromStatus", data.get("fromStatus"));
        if (data.get("reason") != null) {
            details.put("reason", data.get("reason"));
        }
        if (data.get("failureReason") != null) {
            details.put("failureReason", data.get("failureReason"));
        }
        data.forEach(
                (key, value) -> {
                    if (!details.containsKey(key) && isAuditExtra(key)) {
                        details.put(key, value);
                    }
                });
        return envelopeFactory.toJson(details);
    }

    private static boolean isAuditExtra(String key) {
        return switch (key) {
            case "amount",
                    "currency",
                    "reference",
                    "retryCount",
                    "payerAccountMasked",
                    "payeeAccountMasked",
                    "nextRetryAt" ->
                    false;
            default -> true;
        };
    }
}
