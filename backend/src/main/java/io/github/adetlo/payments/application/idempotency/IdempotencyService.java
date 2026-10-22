package io.github.adetlo.payments.application.idempotency;

import io.github.adetlo.payments.application.CreatePaymentCommand;
import io.github.adetlo.payments.application.port.IdempotencyLock;
import io.github.adetlo.payments.config.PaymentsProperties;
import io.github.adetlo.payments.domain.IdempotencyRecord;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.infrastructure.persistence.IdempotencyRecordRepository;
import io.github.adetlo.payments.observability.PaymentMetrics;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Implements the idempotency protocol for {@code POST /api/v1/payments}.
 *
 * <p>The Redis lock only serialises racing requests early; the primary key of {@code
 * idempotency_records} is the actual guarantee, so a Redis outage never allows duplicates.
 */
@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
    private static final int CREATED = 201;

    private final IdempotencyRecordRepository repository;
    private final IdempotencyLock lock;
    private final PaymentMetrics metrics;
    private final Clock clock;
    private final Duration ttl;
    private final TransactionTemplate transaction;

    public IdempotencyService(
            IdempotencyRecordRepository repository,
            IdempotencyLock lock,
            PaymentMetrics metrics,
            Clock clock,
            PaymentsProperties properties,
            PlatformTransactionManager transactionManager) {
        this.repository = repository;
        this.lock = lock;
        this.metrics = metrics;
        this.clock = clock;
        this.ttl = properties.idempotency().ttl();
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Runs {@code create} at most once per key. {@code render} turns the payment into the response
     * body that is stored and replayed verbatim.
     */
    public IdempotentResult execute(
            CreatePaymentCommand command,
            Function<CreatePaymentCommand, Payment> create,
            Function<Payment, String> render) {
        String key = command.idempotencyKey();
        if (key == null) {
            Payment payment = create.apply(command);
            return new IdempotentResult(CREATED, render.apply(payment), payment.paymentId(), false);
        }
        String hash = RequestHasher.hash(command);
        if (!lock.tryAcquire(key)) {
            throw new IdempotencyInProgressException(key);
        }
        try {
            Optional<IdempotencyRecord> existing = repository.findById(key);
            if (existing.isPresent()) {
                return replay(existing.get(), hash);
            }
            if (!claimKey(key, hash)) {
                IdempotencyRecord record =
                        repository
                                .findById(key)
                                .orElseThrow(() -> new IdempotencyInProgressException(key));
                return replay(record, hash);
            }
            return createAndStore(command, create, render, key);
        } finally {
            lock.release(key);
        }
    }

    private IdempotentResult createAndStore(
            CreatePaymentCommand command,
            Function<CreatePaymentCommand, Payment> create,
            Function<Payment, String> render,
            String key) {
        try {
            Payment payment = create.apply(command);
            String body = render.apply(payment);
            complete(key, payment.paymentId(), body);
            return new IdempotentResult(CREATED, body, payment.paymentId(), false);
        } catch (RuntimeException e) {
            transaction.executeWithoutResult(status -> repository.deleteById(key));
            throw e;
        }
    }

    private IdempotentResult replay(IdempotencyRecord record, String hash) {
        if (!record.matches(hash)) {
            metrics.idempotencyConflict();
            throw new IdempotencyKeyReuseException(record.idempotencyKey());
        }
        if (record.isInProgress()) {
            throw new IdempotencyInProgressException(record.idempotencyKey());
        }
        metrics.idempotencyReplayed();
        log.info("Replaying idempotent response for key {}", record.idempotencyKey());
        return new IdempotentResult(
                record.responseStatus(), record.responseBody(), record.paymentId(), true);
    }

    private boolean claimKey(String key, String hash) {
        Instant now = clock.instant();
        Integer inserted =
                transaction.execute(
                        status -> repository.insertInProgress(key, hash, now, now.plus(ttl)));
        return inserted != null && inserted == 1;
    }

    private void complete(String key, UUID paymentId, String body) {
        transaction.executeWithoutResult(
                status -> {
                    IdempotencyRecord record =
                            repository
                                    .findById(key)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalStateException(
                                                            "Idempotency record vanished: " + key));
                    record.complete(paymentId, CREATED, body);
                    repository.save(record);
                });
    }

    /** Removes expired records; scheduled hourly. */
    public int purgeExpired() {
        Integer removed = transaction.execute(status -> repository.deleteExpired(clock.instant()));
        int count = removed == null ? 0 : removed;
        if (count > 0) {
            log.info("Purged {} expired idempotency records", count);
        }
        return count;
    }
}
