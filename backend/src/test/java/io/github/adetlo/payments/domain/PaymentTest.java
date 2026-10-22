package io.github.adetlo.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PaymentTest {

    @Test
    void createStartsInReceivedWithDefaults() {
        Instant now = Instant.parse("2026-10-22T10:00:00Z");
        Payment payment =
                Payment.create(
                        PaymentFixtures.instruction(),
                        "order-1",
                        "corr-1",
                        DemoFailureMode.SETTLEMENT_TIMEOUT,
                        3,
                        now);

        assertThat(payment.paymentId()).isNotNull();
        assertThat(payment.status()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(payment.retryCount()).isZero();
        assertThat(payment.maxRetries()).isEqualTo(3);
        assertThat(payment.nextRetryAt()).isNull();
        assertThat(payment.failureReason()).isNull();
        assertThat(payment.idempotencyKey()).isEqualTo("order-1");
        assertThat(payment.correlationId()).isEqualTo("corr-1");
        assertThat(payment.demoFailureMode()).isEqualTo(DemoFailureMode.SETTLEMENT_TIMEOUT);
        assertThat(payment.createdAt()).isEqualTo(now);
        assertThat(payment.updatedAt()).isEqualTo(now);
        assertThat(payment.money()).isEqualTo(Money.of("125.50", "GBP"));
        assertThat(payment.instruction()).isEqualTo(PaymentFixtures.instruction());
        assertThat(payment.version()).isNull();
    }

    @Test
    void retryPendingIncrementsCountAndSchedulesExponentialBackoff() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        Instant now = Instant.parse("2026-10-22T10:00:00Z");

        payment.transitionTo(PaymentStatus.RETRY_PENDING, now, "timeout");

        assertThat(payment.retryCount()).isEqualTo(1);
        assertThat(payment.nextRetryAt()).isEqualTo(now.plusSeconds(1));
        assertThat(payment.failureReason()).isEqualTo("timeout");
        assertThat(payment.isRetryDue(now)).isFalse();
        assertThat(payment.isRetryDue(now.plusSeconds(1))).isTrue();

        payment.transitionTo(PaymentStatus.PROCESSING, now.plusSeconds(1), null);
        assertThat(payment.nextRetryAt()).isNull();
        payment.transitionTo(PaymentStatus.RETRY_PENDING, now.plusSeconds(1), "timeout again");
        assertThat(payment.retryCount()).isEqualTo(2);
        assertThat(payment.nextRetryAt()).isEqualTo(now.plusSeconds(1 + 2));
    }

    @Test
    void backoffIsCappedAtThirtySeconds() {
        assertThat(Payment.backoffFor(0)).isEqualTo(Duration.ofSeconds(1));
        assertThat(Payment.backoffFor(1)).isEqualTo(Duration.ofSeconds(2));
        assertThat(Payment.backoffFor(2)).isEqualTo(Duration.ofSeconds(4));
        assertThat(Payment.backoffFor(4)).isEqualTo(Duration.ofSeconds(16));
        assertThat(Payment.backoffFor(5)).isEqualTo(Payment.MAX_RETRY_BACKOFF);
        assertThat(Payment.backoffFor(40)).isEqualTo(Payment.MAX_RETRY_BACKOFF);
    }

    @Test
    void retriesExhaustedAfterMaxRetries() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        Instant now = Instant.now();
        for (int i = 0; i < PaymentFixtures.MAX_RETRIES; i++) {
            assertThat(payment.retriesExhausted()).isFalse();
            payment.transitionTo(PaymentStatus.RETRY_PENDING, now, "timeout");
            payment.transitionTo(PaymentStatus.PROCESSING, now, null);
        }

        assertThat(payment.retryCount()).isEqualTo(3);
        assertThat(payment.retriesExhausted()).isTrue();
    }

    @Test
    void completionClearsFailureStateAndFailureKeepsReason() {
        Payment completed = PaymentFixtures.paymentIn(PaymentStatus.RETRY_PENDING);
        Instant now = Instant.now();
        completed.transitionTo(PaymentStatus.PROCESSING, now, null);
        completed.transitionTo(PaymentStatus.COMPLETED, now, null);
        assertThat(completed.failureReason()).isNull();
        assertThat(completed.nextRetryAt()).isNull();
        assertThat(completed.updatedAt()).isEqualTo(now);

        Payment failed = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        failed.transitionTo(PaymentStatus.FAILED, now, "rejected by network");
        assertThat(failed.failureReason()).isEqualTo("rejected by network");

        Payment rejected = PaymentFixtures.payment();
        rejected.transitionTo(PaymentStatus.REJECTED, now, "payer equals payee");
        assertThat(rejected.failureReason()).isEqualTo("payer equals payee");
    }

    @Test
    void cancellationRecordsReasonAndClearsRetry() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.RETRY_PENDING);

        payment.transitionTo(PaymentStatus.CANCELLED, Instant.now(), "client request");

        assertThat(payment.status()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.nextRetryAt()).isNull();
        assertThat(payment.failureReason()).isEqualTo("client request");
    }
}
