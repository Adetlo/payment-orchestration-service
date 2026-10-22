package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.application.idempotency.IdempotencyService;
import io.github.adetlo.payments.domain.IdempotencyRecord;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.IdempotencyRecordRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentSpecifications;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

class RepositoryIT extends IntegrationTestSupport {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private IdempotencyService idempotencyService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void paymentRoundTripsThroughJpaWithVersioning() {
        Payment saved =
                paymentRepository.save(
                        PaymentFixtures.paymentIn(PaymentStatus.RETRY_PENDING, "1500", "IDR"));

        Payment reloaded = paymentRepository.findById(saved.paymentId()).orElseThrow();

        assertThat(reloaded.money().amount()).isEqualByComparingTo("1500");
        assertThat(reloaded.money().currencyCode()).isEqualTo("IDR");
        assertThat(reloaded.status()).isEqualTo(PaymentStatus.RETRY_PENDING);
        assertThat(reloaded.retryCount()).isEqualTo(1);
        assertThat(reloaded.nextRetryAt()).isNotNull();
        assertThat(reloaded.version()).isEqualTo(0L);
        assertThat(reloaded.instruction().payerAccount().value()).isEqualTo(PaymentFixtures.PAYER);
    }

    @Test
    void specificationFiltersCombine() {
        Payment target =
                paymentRepository.save(
                        PaymentFixtures.paymentIn(PaymentStatus.FAILED, "77.00", "USD"));
        Instant from = target.createdAt().minusSeconds(1);
        Instant to = target.createdAt().plusSeconds(1);

        List<Payment> matched =
                paymentRepository
                        .findAll(
                                PaymentSpecifications.filter(PaymentStatus.FAILED, "usd", from, to),
                                PageRequest.of(0, 100, Sort.by("createdAt")))
                        .getContent();
        List<Payment> wrongStatus =
                paymentRepository
                        .findAll(
                                PaymentSpecifications.filter(
                                        PaymentStatus.COMPLETED, "USD", from, to),
                                PageRequest.of(0, 100))
                        .getContent();

        assertThat(matched).extracting(Payment::paymentId).contains(target.paymentId());
        assertThat(matched)
                .allMatch(
                        p ->
                                p.status() == PaymentStatus.FAILED
                                        && p.money().currencyCode().equals("USD"));
        assertThat(wrongStatus).extracting(Payment::paymentId).doesNotContain(target.paymentId());
    }

    @Test
    void dueRetriesQueryOnlyReturnsRetryPendingRowsThatAreDue() {
        Payment due = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        due.transitionTo(
                PaymentStatus.RETRY_PENDING, Instant.now().plus(Duration.ofDays(30)), "later");
        Payment notDue = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        notDue.transitionTo(
                PaymentStatus.RETRY_PENDING, Instant.now().plus(Duration.ofDays(60)), "much later");
        paymentRepository.saveAll(List.of(due, notDue));
        Instant probe = Instant.now().plus(Duration.ofDays(45));

        List<Payment> found =
                transactionTemplate.execute(
                        s -> paymentRepository.findDueRetriesForUpdate(probe, 1000));

        assertThat(found)
                .extracting(Payment::paymentId)
                .contains(due.paymentId())
                .doesNotContain(notDue.paymentId());
        assertThat(paymentRepository.countByStatusGrouped())
                .anyMatch(c -> c.getStatus() == PaymentStatus.RETRY_PENDING && c.getCount() >= 2);
    }

    @Test
    void idempotencyInsertIsConflictFreeAndExpiredRecordsArePurged() {
        String key = "repo-" + PaymentFixtures.uniqueSuffix();
        Instant now = Instant.now();

        Integer first =
                transactionTemplate.execute(
                        s ->
                                idempotencyRecordRepository.insertInProgress(
                                        key, "h", now, now.minusSeconds(1)));
        Integer second =
                transactionTemplate.execute(
                        s ->
                                idempotencyRecordRepository.insertInProgress(
                                        key, "h", now, now.plusSeconds(60)));
        Optional<IdempotencyRecord> stored = idempotencyRecordRepository.findById(key);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        assertThat(stored).isPresent();
        assertThat(stored.get().isInProgress()).isTrue();
        assertThat(stored.get().expiresAt()).isBefore(now);

        assertThat(idempotencyService.purgeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(idempotencyRecordRepository.findById(key)).isEmpty();
    }
}
