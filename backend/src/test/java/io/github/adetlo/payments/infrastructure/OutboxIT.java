package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.github.adetlo.payments.application.PaymentStateMachine;
import io.github.adetlo.payments.application.TransitionContext;
import io.github.adetlo.payments.application.event.KafkaTopics;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.OutboxEvent;
import io.github.adetlo.payments.domain.OutboxStatus;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.OutboxEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentEventRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.support.ApiClient.ApiResponse;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import io.github.adetlo.payments.support.KafkaTestConsumer;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class OutboxIT extends IntegrationTestSupport {

    @Autowired private OutboxEventRepository outboxEventRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private PaymentEventRepository paymentEventRepository;
    @Autowired private AuditEntryRepository auditEntryRepository;
    @Autowired private PaymentStateMachine stateMachine;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void outboxRowIsPublishedToKafkaAndMarkedPublished() {
        try (KafkaTestConsumer consumer =
                new KafkaTestConsumer(bootstrapServers(), KafkaTopics.PAYMENT_EVENTS)) {
            ApiResponse created = api.createPayment(PaymentFixtures.createRequest());
            String id = created.text("paymentId");

            ConsumerRecord<String, String> record =
                    consumer.awaitRecord(r -> id.equals(r.key()), Duration.ofSeconds(30));
            await().atMost(Duration.ofSeconds(10))
                    .untilAsserted(
                            () -> {
                                List<OutboxEvent> rows =
                                        outboxEventRepository.findByAggregateIdOrderByIdAsc(id);
                                assertThat(rows).isNotEmpty();
                                assertThat(rows.get(0).status()).isEqualTo(OutboxStatus.PUBLISHED);
                                assertThat(rows.get(0).publishedAt()).isNotNull();
                                assertThat(rows.get(0).attempts()).isZero();
                            });

            assertThat(record.value())
                    .isEqualTo(
                            outboxEventRepository
                                    .findByAggregateIdOrderByIdAsc(id)
                                    .get(0)
                                    .payload());
        }
    }

    @Test
    void stateChangeAndOutboxWriteAreAtomic() {
        Payment payment = paymentRepository.save(PaymentFixtures.payment());
        String id = payment.paymentId().toString();

        assertThatThrownBy(
                        () ->
                                transactionTemplate.executeWithoutResult(
                                        status -> {
                                            Payment managed =
                                                    paymentRepository
                                                            .findById(payment.paymentId())
                                                            .orElseThrow();
                                            stateMachine.transition(
                                                    managed,
                                                    PaymentStatus.VALIDATED,
                                                    TransitionContext.of(AuditActor.CONSUMER));
                                            paymentRepository.flush();
                                            throw new IllegalStateException(
                                                    "simulated failure after the writes");
                                        }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(paymentRepository.findById(payment.paymentId()).orElseThrow().status())
                .isEqualTo(PaymentStatus.RECEIVED);
        assertThat(paymentEventRepository.countByPaymentId(payment.paymentId())).isZero();
        assertThat(auditEntryRepository.countByPaymentId(payment.paymentId())).isZero();
        assertThat(outboxEventRepository.findByAggregateIdOrderByIdAsc(id)).isEmpty();
    }

    @Test
    void claimBatchSkipsRowsLockedByAnotherPublisher() throws Exception {
        Instant future = Instant.now().plus(Duration.ofDays(1));
        String aggregate = "locktest-" + PaymentFixtures.uniqueSuffix();
        outboxEventRepository.saveAll(
                List.of(
                        OutboxEvent.of(
                                OutboxEvent.AGGREGATE_PAYMENT,
                                aggregate,
                                "PaymentReceived",
                                1,
                                "{}",
                                future),
                        OutboxEvent.of(
                                OutboxEvent.AGGREGATE_PAYMENT,
                                aggregate,
                                "PaymentReceived",
                                1,
                                "{}",
                                future)));
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<List<Long>> first =
                    pool.submit(
                            () ->
                                    transactionTemplate.execute(
                                            s -> {
                                                List<Long> ids =
                                                        outboxEventRepository
                                                                .claimBatch(
                                                                        future.plusSeconds(1), 1000)
                                                                .stream()
                                                                .filter(
                                                                        e ->
                                                                                aggregate.equals(
                                                                                        e
                                                                                                .aggregateId()))
                                                                .map(OutboxEvent::id)
                                                                .toList();
                                                firstClaimed.countDown();
                                                try {
                                                    release.await(20, TimeUnit.SECONDS);
                                                } catch (InterruptedException e) {
                                                    Thread.currentThread().interrupt();
                                                }
                                                return ids;
                                            }));
            assertThat(firstClaimed.await(20, TimeUnit.SECONDS)).isTrue();
            Future<List<Long>> second =
                    pool.submit(
                            () ->
                                    transactionTemplate.execute(
                                            s ->
                                                    outboxEventRepository
                                                            .claimBatch(future.plusSeconds(1), 1000)
                                                            .stream()
                                                            .filter(
                                                                    e ->
                                                                            aggregate.equals(
                                                                                    e
                                                                                            .aggregateId()))
                                                            .map(OutboxEvent::id)
                                                            .toList()));
            List<Long> secondIds = second.get(20, TimeUnit.SECONDS);
            release.countDown();
            List<Long> firstIds = first.get(20, TimeUnit.SECONDS);

            assertThat(firstIds).hasSize(2);
            assertThat(secondIds).isEmpty();
        } finally {
            release.countDown();
            pool.shutdownNow();
            transactionTemplate.executeWithoutResult(
                    s ->
                            outboxEventRepository.deleteAll(
                                    outboxEventRepository.findByAggregateIdOrderByIdAsc(
                                            aggregate)));
        }
    }
}
