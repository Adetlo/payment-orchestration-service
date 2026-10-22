package io.github.adetlo.payments.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.application.CreatePaymentCommand;
import io.github.adetlo.payments.application.idempotency.RequestHasher;
import io.github.adetlo.payments.infrastructure.persistence.IdempotencyRecordRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.support.ApiClient.ApiResponse;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class IdempotencyIT extends IntegrationTestSupport {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private IdempotencyRecordRepository idempotencyRecordRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private StringRedisTemplate redis;

    @Test
    void replayReturnsStoredResponseWithReplayHeader() {
        String key = "order-" + PaymentFixtures.uniqueSuffix();
        Map<String, Object> body = PaymentFixtures.createRequest();

        ApiResponse first = api.createPayment(body, key);
        ApiResponse replay = api.createPayment(body, key);

        assertThat(first.status()).isEqualTo(201);
        assertThat(first.header("Idempotent-Replayed")).isNull();
        assertThat(first.text("idempotencyKey")).isEqualTo(key);
        assertThat(replay.status()).isEqualTo(201);
        assertThat(replay.header("Idempotent-Replayed")).isEqualTo("true");
        assertThat(replay.header("Location")).isEqualTo(first.header("Location"));
        assertThat(replay.raw()).isEqualTo(first.raw());
        assertThat(paymentRepository.countByIdempotencyKey(key)).isEqualTo(1);
    }

    @Test
    void differentPayloadWithSameKeyIsAConflict() {
        String key = "order-" + PaymentFixtures.uniqueSuffix();
        Map<String, Object> body = PaymentFixtures.createRequest();

        ApiResponse first = api.createPayment(body, key);
        body.put("amount", "999.99");
        ApiResponse conflict = api.createPayment(body, key);

        assertThat(first.status()).isEqualTo(201);
        assertThat(conflict.status()).isEqualTo(409);
        assertThat(conflict.text("type")).isEqualTo("about:blank#idempotency-key-reuse");
        assertThat(conflict.header("Content-Type")).startsWith("application/problem+json");
        assertThat(paymentRepository.countByIdempotencyKey(key)).isEqualTo(1);
    }

    @Test
    void inProgressRecordYields409WithRetryAfter() {
        String key = "order-" + PaymentFixtures.uniqueSuffix();
        Map<String, Object> body = PaymentFixtures.createRequest();
        CreatePaymentCommand command =
                new CreatePaymentCommand(
                        (String) body.get("payerAccount"),
                        (String) body.get("payeeAccount"),
                        (String) body.get("amount"),
                        (String) body.get("currency"),
                        (String) body.get("reference"),
                        key,
                        null,
                        "c");
        Instant now = Instant.now();
        transactionTemplate.executeWithoutResult(
                s ->
                        idempotencyRecordRepository.insertInProgress(
                                key,
                                RequestHasher.hash(command),
                                now,
                                now.plus(Duration.ofHours(1))));

        ApiResponse response = api.createPayment(body, key);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("type")).isEqualTo("about:blank#idempotency-request-in-progress");
        assertThat(response.header("Retry-After")).isEqualTo("1");
        assertThat(paymentRepository.countByIdempotencyKey(key)).isZero();
    }

    @Test
    void heldRedisLockYields409InProgressWithoutTouchingTheDatabase() {
        String key = "order-" + PaymentFixtures.uniqueSuffix();
        redis.opsForValue().set("idem:" + key, "1", Duration.ofSeconds(30));

        ApiResponse response = api.createPayment(PaymentFixtures.createRequest(), key);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.text("type")).isEqualTo("about:blank#idempotency-request-in-progress");
        assertThat(idempotencyRecordRepository.findById(key)).isEmpty();
        redis.delete("idem:" + key);
    }

    @Test
    void withoutKeyEveryRequestCreatesAPayment() {
        Map<String, Object> body = PaymentFixtures.createRequest();

        ApiResponse first = api.createPayment(body);
        ApiResponse second = api.createPayment(body);

        assertThat(first.status()).isEqualTo(201);
        assertThat(second.status()).isEqualTo(201);
        assertThat(first.text("paymentId")).isNotEqualTo(second.text("paymentId"));
    }

    @Test
    void keyLongerThan128CharactersIsRejected() {
        ApiResponse response = api.createPayment(PaymentFixtures.createRequest(), "k".repeat(129));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    void twentyConcurrentRequestsWithTheSameKeyCreateExactlyOnePayment() throws Exception {
        String key = "concurrent-" + PaymentFixtures.uniqueSuffix();
        Map<String, Object> body = PaymentFixtures.createRequest();
        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ApiResponse>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await(10, TimeUnit.SECONDS);
                                    return api.createPayment(body, key);
                                }));
            }
            start.countDown();
            List<ApiResponse> responses = new ArrayList<>();
            for (Future<ApiResponse> future : futures) {
                responses.add(future.get(60, TimeUnit.SECONDS));
            }

            assertThat(paymentRepository.countByIdempotencyKey(key)).isEqualTo(1);
            assertThat(responses)
                    .extracting(ApiResponse::status)
                    .containsOnly(201, 409)
                    .contains(201);
            List<String> createdIds =
                    responses.stream()
                            .filter(r -> r.status() == 201)
                            .map(r -> r.text("paymentId"))
                            .distinct()
                            .toList();
            assertThat(createdIds).hasSize(1);
            assertThat(responses.stream().filter(r -> r.status() == 409).map(r -> r.text("type")))
                    .allMatch("about:blank#idempotency-request-in-progress"::equals);
            ApiResponse replay = api.createPayment(body, key);
            assertThat(replay.status()).isEqualTo(201);
            assertThat(replay.header("Idempotent-Replayed")).isEqualTo("true");
            assertThat(replay.text("paymentId")).isEqualTo(createdIds.get(0));
        } finally {
            pool.shutdownNow();
        }
    }
}
