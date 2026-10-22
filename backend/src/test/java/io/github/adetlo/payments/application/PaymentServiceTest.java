package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.DomainValidationException;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.support.PaymentFixtures;
import io.github.adetlo.payments.support.TestProperties;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentServiceTest {

    private final PaymentRepository paymentRepository = mock(PaymentRepository.class);
    private final PaymentStateMachine stateMachine = mock(PaymentStateMachine.class);

    private PaymentService service(boolean failureInjection) {
        when(stateMachine.start(any(), any())).thenAnswer(inv -> inv.getArgument(0));
        when(stateMachine.transition(any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            Payment p = inv.getArgument(0);
                            TransitionContext ctx = inv.getArgument(2);
                            p.transitionTo(
                                    inv.getArgument(1), java.time.Instant.now(), ctx.reason());
                            return p;
                        });
        return new PaymentService(
                paymentRepository,
                mock(PaymentEventRepository.class),
                mock(AuditEntryRepository.class),
                stateMachine,
                new FailureInjectionPolicy(TestProperties.withFailureInjection(failureInjection)),
                TestProperties.defaults(),
                Clock.systemUTC());
    }

    @Test
    void createBuildsReceivedPaymentWithDemoModeWhenInjectionEnabled() {
        Payment payment =
                service(true).create(PaymentFixtures.command("order-1", "settlement-timeout"));

        assertThat(payment.status()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(payment.idempotencyKey()).isEqualTo("order-1");
        assertThat(payment.correlationId()).isEqualTo(PaymentFixtures.CORRELATION_ID);
        assertThat(payment.demoFailureMode()).isEqualTo(DemoFailureMode.SETTLEMENT_TIMEOUT);
        assertThat(payment.maxRetries()).isEqualTo(3);
        ArgumentCaptor<TransitionContext> ctx = ArgumentCaptor.forClass(TransitionContext.class);
        verify(stateMachine).start(eq(payment), ctx.capture());
        assertThat(ctx.getValue().actor()).isEqualTo(AuditActor.API);
        assertThat(ctx.getValue().extraDetails())
                .containsEntry("demoFailureMode", "settlement-timeout")
                .containsEntry("idempotencyKey", "order-1");
    }

    @Test
    void demoFailureHeaderIsSilentlyIgnoredAndAuditedWhenInjectionDisabled() {
        Payment payment =
                service(false).create(PaymentFixtures.command(null, "settlement-rejected"));

        assertThat(payment.demoFailureMode()).isNull();
        ArgumentCaptor<TransitionContext> ctx = ArgumentCaptor.forClass(TransitionContext.class);
        verify(stateMachine).start(eq(payment), ctx.capture());
        assertThat(ctx.getValue().extraDetails())
                .containsEntry("demoFailureIgnored", "settlement-rejected")
                .doesNotContainKey("demoFailureMode");
    }

    @Test
    void unknownDemoFailureIsAValidationErrorWhenInjectionEnabled() {
        PaymentService service = service(true);
        CreatePaymentCommand command = PaymentFixtures.command(null, "explode");

        assertThatThrownBy(() -> service.create(command))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(
                        e ->
                                assertThat(((DomainValidationException) e).field())
                                        .isEqualTo("X-Demo-Failure"));
        verify(stateMachine, never()).start(any(), any());
    }

    @Test
    void domainRulesAreAppliedToTheCommand() {
        PaymentService service = service(true);

        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePaymentCommand(
                                                PaymentFixtures.PAYER,
                                                PaymentFixtures.PAYEE,
                                                "0.00",
                                                "GBP",
                                                "X",
                                                null,
                                                null,
                                                "c")))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("greater than zero");
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePaymentCommand(
                                                "bad",
                                                PaymentFixtures.PAYEE,
                                                "1.00",
                                                "GBP",
                                                "X",
                                                null,
                                                null,
                                                "c")))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(
                        e ->
                                assertThat(((DomainValidationException) e).field())
                                        .isEqualTo("payerAccount"));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        new CreatePaymentCommand(
                                                PaymentFixtures.PAYER,
                                                PaymentFixtures.PAYEE,
                                                "1.005",
                                                "GBP",
                                                "X",
                                                null,
                                                null,
                                                "c")))
                .isInstanceOf(DomainValidationException.class)
                .satisfies(
                        e ->
                                assertThat(((DomainValidationException) e).field())
                                        .isEqualTo("amount"));
    }

    @Test
    void cancelUsesDefaultReasonAndNotFoundIsReported() {
        PaymentService service = service(true);
        Payment payment = PaymentFixtures.payment();
        when(paymentRepository.findById(payment.paymentId())).thenReturn(Optional.of(payment));
        UUID unknown = UUID.randomUUID();
        when(paymentRepository.findById(unknown)).thenReturn(Optional.empty());

        Payment cancelled = service.cancel(payment.paymentId(), " ");

        assertThat(cancelled.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(cancelled.failureReason()).isEqualTo("Cancelled by client");
        assertThatThrownBy(() -> service.cancel(unknown, null))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> service.get(unknown)).isInstanceOf(PaymentNotFoundException.class);
    }
}
