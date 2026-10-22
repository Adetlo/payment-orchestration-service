package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import io.github.adetlo.payments.support.PaymentFixtures;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class PaymentStateMachineTest {

    private static final Instant NOW = Instant.parse("2099-01-01T00:00:00Z");

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentEventRepository eventRepository = mock(PaymentEventRepository.class);
    private final AuditEntryRepository auditRepository = mock(AuditEntryRepository.class);
    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private PaymentStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        when(paymentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stateMachine =
                new PaymentStateMachine(
                        paymentRepository,
                        eventRepository,
                        auditRepository,
                        outboxRepository,
                        new EventEnvelopeFactory(Jackson2ObjectMapperBuilder.json().build()),
                        new PaymentMetrics(registry, outboxRepository),
                        Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishedTransitionWritesEventAuditAndOutbox() {
        Payment payment = PaymentFixtures.payment();

        Payment result =
                stateMachine.transition(
                        payment,
                        PaymentStatus.VALIDATED,
                        TransitionContext.of(AuditActor.CONSUMER));

        assertThat(result.status()).isEqualTo(PaymentStatus.VALIDATED);
        ArgumentCaptor<PaymentEvent> event = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(PaymentEventType.PaymentValidated);
        assertThat(event.getValue().fromStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(event.getValue().toStatus()).isEqualTo(PaymentStatus.VALIDATED);
        assertThat(event.getValue().occurredAt()).isEqualTo(NOW);
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("payment.validated");
        assertThat(audit.getValue().actor()).isEqualTo(AuditActor.CONSUMER);
        ArgumentCaptor<OutboxEvent> outbox = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxRepository).save(outbox.capture());
        assertThat(outbox.getValue().eventType()).isEqualTo("PaymentValidated");
        assertThat(outbox.getValue().aggregateId()).isEqualTo(payment.paymentId().toString());
        assertThat(outbox.getValue().payload())
                .contains("\"eventType\":\"PaymentValidated\"")
                .doesNotContain(PaymentFixtures.PAYER);
    }

    @Test
    void internalTransitionWritesNoOutboxRow() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.VALIDATED);

        stateMachine.transition(
                payment, PaymentStatus.PROCESSING, TransitionContext.of(AuditActor.CONSUMER));

        verify(eventRepository).save(any());
        verify(auditRepository).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void invalidTransitionThrowsAndWritesNothing() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.COMPLETED);

        assertThatThrownBy(
                        () ->
                                stateMachine.transition(
                                        payment,
                                        PaymentStatus.CANCELLED,
                                        TransitionContext.of(AuditActor.API)))
                .isInstanceOf(InvalidStateTransitionException.class);

        verify(paymentRepository, never()).save(any());
        verify(eventRepository, never()).save(any());
        verify(auditRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
        assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void startWritesPaymentReceivedAndCountsCreation() {
        Payment payment = PaymentFixtures.payment();

        stateMachine.start(
                payment,
                new TransitionContext(AuditActor.API, null, Map.of("idempotencyKey", "k1")));

        ArgumentCaptor<PaymentEvent> event = ArgumentCaptor.forClass(PaymentEvent.class);
        verify(eventRepository).save(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo(PaymentEventType.PaymentReceived);
        assertThat(event.getValue().fromStatus()).isNull();
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository).save(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("payment.created");
        assertThat(audit.getValue().details()).contains("\"idempotencyKey\":\"k1\"");
        verify(outboxRepository).save(any());
        assertThat(registry.get("payment.received").counter().count()).isEqualTo(1.0);
    }

    @Test
    void terminalTransitionRecordsProcessingDurationAndOutcomeCounter() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);

        stateMachine.transition(
                payment, PaymentStatus.COMPLETED, TransitionContext.of(AuditActor.CONSUMER));
        stateMachine.transition(
                PaymentFixtures.paymentIn(PaymentStatus.PROCESSING),
                PaymentStatus.RETRY_PENDING,
                TransitionContext.of(AuditActor.CONSUMER, "slow"));

        assertThat(registry.get("payment.completed").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("payment.retry").counter().count()).isEqualTo(1.0);
        assertThat(registry.get("payment.processing.duration").timer().count()).isEqualTo(1);
    }
}
