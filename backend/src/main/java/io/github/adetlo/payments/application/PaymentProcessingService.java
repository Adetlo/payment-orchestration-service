package io.github.adetlo.payments.application;

import io.github.adetlo.payments.application.port.PoisonMessageException;
import io.github.adetlo.payments.application.port.SettlementResult;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.Money;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentInstruction;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional steps of the processing flow. Each public method is one database transaction; the
 * settlement call itself happens outside of them (see {@link SettlementRunner}).
 */
@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentStateMachine stateMachine;
    private final FailureInjectionPolicy failureInjection;
    private final Clock clock;

    public PaymentProcessingService(
            PaymentRepository paymentRepository,
            PaymentStateMachine stateMachine,
            FailureInjectionPolicy failureInjection,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.stateMachine = stateMachine;
        this.failureInjection = failureInjection;
        this.clock = clock;
    }

    /**
     * Handles a {@code PaymentReceived} event: validates and, when valid, moves the payment to
     * PROCESSING. Returns {@code true} when settlement should be attempted next. Re-deliveries of
     * events whose status no longer applies are ignored, which makes the consumer idempotent.
     */
    @Transactional
    public boolean onPaymentReceived(UUID paymentId) {
        Optional<Payment> found = paymentRepository.findById(paymentId);
        if (found.isEmpty()) {
            log.warn("Ignoring PaymentReceived for unknown payment {}", paymentId);
            return false;
        }
        Payment payment = found.get();
        if (failureInjection.isActive(payment, DemoFailureMode.POISON_MESSAGE)) {
            throw new PoisonMessageException(paymentId);
        }
        if (payment.status() == PaymentStatus.PROCESSING) {
            log.info("Payment {} already PROCESSING; resuming settlement", paymentId);
            return true;
        }
        if (payment.status() != PaymentStatus.RECEIVED) {
            log.info(
                    "Ignoring PaymentReceived for payment {} in status {}",
                    paymentId,
                    payment.status());
            return false;
        }
        Optional<String> rejection = validate(payment.instruction());
        if (rejection.isPresent()) {
            stateMachine.transition(
                    payment,
                    PaymentStatus.REJECTED,
                    TransitionContext.of(AuditActor.CONSUMER, rejection.get()));
            log.info("Payment {} rejected: {}", paymentId, rejection.get());
            return false;
        }
        Payment validated =
                stateMachine.transition(
                        payment,
                        PaymentStatus.VALIDATED,
                        TransitionContext.of(AuditActor.CONSUMER));
        stateMachine.transition(
                validated, PaymentStatus.PROCESSING, TransitionContext.of(AuditActor.CONSUMER));
        return true;
    }

    /**
     * Business validation performed by the consumer (format checks already happened at the API).
     */
    static Optional<String> validate(PaymentInstruction instruction) {
        if (instruction.payerEqualsPayee()) {
            return Optional.of("payer and payee accounts must differ");
        }
        if (instruction.money().exceeds(Money.MAX_AMOUNT)) {
            return Optional.of(
                    "amount exceeds the per-payment limit of "
                            + Money.MAX_AMOUNT.toPlainString()
                            + " "
                            + instruction.money().currencyCode());
        }
        return Optional.empty();
    }

    /** The payment to settle, or empty when it is no longer PROCESSING. */
    @Transactional(readOnly = true)
    public Optional<Payment> findForSettlement(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .filter(p -> p.status() == PaymentStatus.PROCESSING);
    }

    /** Applies a settlement result: COMPLETED, RETRY_PENDING or FAILED. */
    @Transactional
    public Optional<Payment> applySettlementResult(
            UUID paymentId, SettlementResult result, AuditActor actor) {
        Optional<Payment> found =
                paymentRepository
                        .findById(paymentId)
                        .filter(p -> p.status() == PaymentStatus.PROCESSING);
        if (found.isEmpty()) {
            log.info("Settlement result for payment {} ignored: no longer PROCESSING", paymentId);
            return Optional.empty();
        }
        Payment payment = found.get();
        Payment updated =
                switch (result.outcome()) {
                    case SETTLED ->
                            stateMachine.transition(
                                    payment, PaymentStatus.COMPLETED, TransitionContext.of(actor));
                    case PERMANENT_FAILURE ->
                            stateMachine.transition(
                                    payment,
                                    PaymentStatus.FAILED,
                                    TransitionContext.of(actor, result.reason()));
                    case TRANSIENT_FAILURE -> scheduleRetryOrFail(payment, result.reason(), actor);
                };
        log.info(
                "Payment {} -> {} (retryCount={})",
                paymentId,
                updated.status(),
                updated.retryCount());
        return Optional.of(updated);
    }

    private Payment scheduleRetryOrFail(Payment payment, String reason, AuditActor actor) {
        if (payment.retriesExhausted()) {
            String message =
                    "retries exhausted after " + payment.retryCount() + " attempts: " + reason;
            return stateMachine.transition(
                    payment, PaymentStatus.FAILED, TransitionContext.of(actor, message));
        }
        return stateMachine.transition(
                payment, PaymentStatus.RETRY_PENDING, TransitionContext.of(actor, reason));
    }

    /** Claims due RETRY_PENDING payments (SKIP LOCKED) and moves them to PROCESSING. */
    @Transactional
    public List<UUID> claimDueRetries(int batchSize) {
        List<Payment> due = paymentRepository.findDueRetriesForUpdate(clock.instant(), batchSize);
        return due.stream()
                .map(
                        p ->
                                stateMachine.transition(
                                        p,
                                        PaymentStatus.PROCESSING,
                                        TransitionContext.of(AuditActor.SCHEDULER)))
                .map(Payment::paymentId)
                .toList();
    }
}
