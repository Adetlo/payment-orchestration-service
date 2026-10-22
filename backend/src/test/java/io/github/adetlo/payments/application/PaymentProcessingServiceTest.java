package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adetlo.payments.application.port.PoisonMessageException;
import io.github.adetlo.payments.application.port.SettlementResult;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.support.PaymentFixtures;
import io.github.adetlo.payments.support.TestProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PaymentProcessingServiceTest {

    private final PaymentRepository repository = mock(PaymentRepository.class);
    private final PaymentStateMachine stateMachine = mock(PaymentStateMachine.class);
    private PaymentProcessingService service;

    @BeforeEach
    void setUp() {
        when(stateMachine.transition(any(), any(), any()))
                .thenAnswer(
                        inv -> {
                            Payment payment = inv.getArgument(0);
                            TransitionContext ctx = inv.getArgument(2);
                            payment.transitionTo(inv.getArgument(1), Instant.now(), ctx.reason());
                            return payment;
                        });
        service =
                new PaymentProcessingService(
                        repository,
                        stateMachine,
                        new FailureInjectionPolicy(TestProperties.withFailureInjection(true)),
                        Clock.systemUTC());
    }

    private Payment stored(Payment payment) {
        when(repository.findById(payment.paymentId())).thenReturn(Optional.of(payment));
        return payment;
    }

    @Test
    void validPaymentMovesToValidatedThenProcessing() {
        Payment payment = stored(PaymentFixtures.payment());

        assertThat(service.onPaymentReceived(payment.paymentId())).isTrue();

        assertThat(payment.status()).isEqualTo(PaymentStatus.PROCESSING);
        verify(stateMachine).transition(eq(payment), eq(PaymentStatus.VALIDATED), any());
        verify(stateMachine).transition(eq(payment), eq(PaymentStatus.PROCESSING), any());
    }

    @Test
    void payerEqualsPayeeIsRejected() {
        Payment payment =
                stored(
                        Payment.create(
                                new io.github.adetlo.payments.domain.PaymentInstruction(
                                        new io.github.adetlo.payments.domain.AccountReference(
                                                PaymentFixtures.PAYER),
                                        new io.github.adetlo.payments.domain.AccountReference(
                                                PaymentFixtures.PAYER),
                                        io.github.adetlo.payments.domain.Money.of("1.00", "GBP"),
                                        "SELF"),
                                null,
                                "c",
                                null,
                                3,
                                Instant.now()));

        assertThat(service.onPaymentReceived(payment.paymentId())).isFalse();

        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.failureReason()).contains("must differ");
    }

    @Test
    void amountAboveLimitIsRejected() {
        Payment payment = stored(PaymentFixtures.payment("1000000.01", "GBP", null));

        assertThat(service.onPaymentReceived(payment.paymentId())).isFalse();

        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.failureReason()).contains("limit");
    }

    @Test
    void unknownOrAlreadyHandledPaymentsAreIgnored() {
        Payment completed = stored(PaymentFixtures.paymentIn(PaymentStatus.COMPLETED));
        UUID unknown = UUID.randomUUID();
        when(repository.findById(unknown)).thenReturn(Optional.empty());

        assertThat(service.onPaymentReceived(unknown)).isFalse();
        assertThat(service.onPaymentReceived(completed.paymentId())).isFalse();

        verify(stateMachine, never()).transition(any(), any(), any());
    }

    @Test
    void processingPaymentResumesSettlement() {
        Payment processing = stored(PaymentFixtures.paymentIn(PaymentStatus.PROCESSING));

        assertThat(service.onPaymentReceived(processing.paymentId())).isTrue();
        verify(stateMachine, never()).transition(any(), any(), any());
    }

    @Test
    void poisonMessageThrowsBeforeAnyTransition() {
        Payment poison =
                stored(PaymentFixtures.payment("1.00", "GBP", DemoFailureMode.POISON_MESSAGE));

        assertThatThrownBy(() -> service.onPaymentReceived(poison.paymentId()))
                .isInstanceOf(PoisonMessageException.class);

        assertThat(poison.status()).isEqualTo(PaymentStatus.RECEIVED);
        verify(stateMachine, never()).transition(any(), any(), any());
    }

    @Test
    void poisonModeIsIgnoredWhenInjectionDisabled() {
        service =
                new PaymentProcessingService(
                        repository,
                        stateMachine,
                        new FailureInjectionPolicy(TestProperties.withFailureInjection(false)),
                        Clock.systemUTC());
        Payment poison =
                stored(PaymentFixtures.payment("1.00", "GBP", DemoFailureMode.POISON_MESSAGE));

        assertThat(service.onPaymentReceived(poison.paymentId())).isTrue();
        assertThat(poison.status()).isEqualTo(PaymentStatus.PROCESSING);
    }

    @Test
    void settledResultCompletesPayment() {
        Payment payment = stored(PaymentFixtures.paymentIn(PaymentStatus.PROCESSING));

        Optional<Payment> result =
                service.applySettlementResult(
                        payment.paymentId(), SettlementResult.settled(), AuditActor.CONSUMER);

        assertThat(result).isPresent();
        assertThat(payment.status()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    void transientFailureSchedulesRetryUntilExhausted() {
        Payment payment = stored(PaymentFixtures.paymentIn(PaymentStatus.PROCESSING));

        service.applySettlementResult(
                payment.paymentId(),
                SettlementResult.transientFailure("timeout"),
                AuditActor.CONSUMER);
        assertThat(payment.status()).isEqualTo(PaymentStatus.RETRY_PENDING);
        assertThat(payment.retryCount()).isEqualTo(1);

        for (int i = 0; i < 2; i++) {
            payment.transitionTo(PaymentStatus.PROCESSING, Instant.now(), null);
            service.applySettlementResult(
                    payment.paymentId(),
                    SettlementResult.transientFailure("timeout"),
                    AuditActor.SCHEDULER);
        }
        assertThat(payment.retryCount()).isEqualTo(3);
        payment.transitionTo(PaymentStatus.PROCESSING, Instant.now(), null);

        service.applySettlementResult(
                payment.paymentId(),
                SettlementResult.transientFailure("timeout"),
                AuditActor.SCHEDULER);

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureReason()).contains("retries exhausted").contains("timeout");
    }

    @Test
    void permanentFailureFailsImmediately() {
        Payment payment = stored(PaymentFixtures.paymentIn(PaymentStatus.PROCESSING));

        service.applySettlementResult(
                payment.paymentId(),
                SettlementResult.permanentFailure("rejected"),
                AuditActor.CONSUMER);

        assertThat(payment.status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(payment.failureReason()).isEqualTo("rejected");
        assertThat(payment.retryCount()).isZero();
    }

    @Test
    void settlementResultIgnoredWhenNotProcessing() {
        Payment cancelled = stored(PaymentFixtures.paymentIn(PaymentStatus.CANCELLED));

        assertThat(
                        service.applySettlementResult(
                                cancelled.paymentId(),
                                SettlementResult.settled(),
                                AuditActor.CONSUMER))
                .isEmpty();
        assertThat(service.findForSettlement(cancelled.paymentId())).isEmpty();
        verify(stateMachine, never()).transition(any(), any(), any());
    }

    @Test
    void claimDueRetriesMovesEachToProcessingAsScheduler() {
        Payment due1 = PaymentFixtures.paymentIn(PaymentStatus.RETRY_PENDING);
        Payment due2 = PaymentFixtures.paymentIn(PaymentStatus.RETRY_PENDING);
        when(repository.findDueRetriesForUpdate(any(), anyInt())).thenReturn(List.of(due1, due2));

        List<UUID> claimed = service.claimDueRetries(20);

        assertThat(claimed).containsExactly(due1.paymentId(), due2.paymentId());
        assertThat(due1.status()).isEqualTo(PaymentStatus.PROCESSING);
        ArgumentCaptor<TransitionContext> ctx = ArgumentCaptor.forClass(TransitionContext.class);
        verify(stateMachine).transition(eq(due2), eq(PaymentStatus.PROCESSING), ctx.capture());
        assertThat(ctx.getValue().actor()).isEqualTo(AuditActor.SCHEDULER);
    }
}
