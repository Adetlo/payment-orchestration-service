package io.github.adetlo.payments.application;

import io.github.adetlo.payments.config.PaymentsProperties;
import io.github.adetlo.payments.domain.AccountReference;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.DomainValidationException;
import io.github.adetlo.payments.domain.Money;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentInstruction;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentSpecifications;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Client-facing payment use cases: create, read, list, cancel and history. */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final AuditEntryRepository auditEntryRepository;
    private final PaymentStateMachine stateMachine;
    private final FailureInjectionPolicy failureInjection;
    private final int maxRetries;
    private final Clock clock;

    public PaymentService(
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            AuditEntryRepository auditEntryRepository,
            PaymentStateMachine stateMachine,
            FailureInjectionPolicy failureInjection,
            PaymentsProperties properties,
            Clock clock) {
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.auditEntryRepository = auditEntryRepository;
        this.stateMachine = stateMachine;
        this.failureInjection = failureInjection;
        this.maxRetries = properties.retry().maxAttempts();
        this.clock = clock;
    }

    /** Validates the command against the domain rules and stores the payment as RECEIVED. */
    @Transactional
    public Payment create(CreatePaymentCommand command) {
        PaymentInstruction instruction = toInstruction(command);
        if (!instruction.money().isPositive()) {
            throw new DomainValidationException("amount", "amount must be greater than zero");
        }
        Map<String, Object> details = new LinkedHashMap<>();
        DemoFailureMode mode = resolveDemoFailure(command.demoFailureHeader(), details);
        if (command.idempotencyKey() != null) {
            details.put("idempotencyKey", command.idempotencyKey());
        }
        Payment payment =
                Payment.create(
                        instruction,
                        command.idempotencyKey(),
                        command.correlationId(),
                        mode,
                        maxRetries,
                        clock.instant());
        Payment saved =
                stateMachine.start(payment, new TransitionContext(AuditActor.API, null, details));
        log.info("Payment {} received ({})", saved.paymentId(), instruction.money());
        return saved;
    }

    @Transactional(readOnly = true)
    public Payment get(UUID paymentId) {
        return paymentRepository
                .findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }

    @Transactional(readOnly = true)
    public Page<Payment> list(PaymentQuery query, int page, int size) {
        PageRequest pageRequest =
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "paymentId"));
        return paymentRepository.findAll(
                PaymentSpecifications.filter(
                        query.status(), query.currency(), query.from(), query.to()),
                pageRequest);
    }

    /** Cancels a payment; only allowed from RECEIVED, VALIDATED and RETRY_PENDING. */
    @Transactional
    public Payment cancel(UUID paymentId, String reason) {
        Payment payment = get(paymentId);
        String cancelReason =
                reason == null || reason.isBlank() ? "Cancelled by client" : reason.trim();
        Payment cancelled =
                stateMachine.transition(
                        payment,
                        PaymentStatus.CANCELLED,
                        TransitionContext.of(AuditActor.API, cancelReason));
        log.info("Payment {} cancelled: {}", paymentId, cancelReason);
        return cancelled;
    }

    @Transactional(readOnly = true)
    public PaymentHistory history(UUID paymentId) {
        Payment payment = get(paymentId);
        return new PaymentHistory(
                payment.paymentId(),
                paymentEventRepository.findByPaymentIdOrderByOccurredAtAscIdAsc(paymentId),
                auditEntryRepository.findByPaymentIdOrderByOccurredAtAscIdAsc(paymentId));
    }

    private static PaymentInstruction toInstruction(CreatePaymentCommand command) {
        AccountReference payer = account("payerAccount", command.payerAccount());
        AccountReference payee = account("payeeAccount", command.payeeAccount());
        Money money = Money.of(command.amount(), command.currency());
        return new PaymentInstruction(payer, payee, money, command.reference());
    }

    private static AccountReference account(String field, String value) {
        try {
            return new AccountReference(value == null ? "" : value.trim());
        } catch (DomainValidationException e) {
            throw new DomainValidationException(field, e.getMessage());
        }
    }

    private DemoFailureMode resolveDemoFailure(String header, Map<String, Object> auditDetails) {
        if (header == null || header.isBlank()) {
            return null;
        }
        if (!failureInjection.isEnabled()) {
            log.info("Ignoring X-Demo-Failure={} because failure injection is disabled", header);
            auditDetails.put("demoFailureIgnored", header);
            return null;
        }
        DemoFailureMode mode =
                DemoFailureMode.fromHeader(header)
                        .orElseThrow(
                                () ->
                                        new DomainValidationException(
                                                "X-Demo-Failure",
                                                "unknown failure mode '"
                                                        + header
                                                        + "' (expected settlement-timeout,"
                                                        + " settlement-rejected or"
                                                        + " poison-message)"));
        auditDetails.put("demoFailureMode", mode.headerValue());
        return mode;
    }
}
