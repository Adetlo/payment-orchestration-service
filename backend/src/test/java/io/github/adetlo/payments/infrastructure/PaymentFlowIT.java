package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.adetlo.payments.application.event.KafkaTopics;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.support.ApiClient.ApiResponse;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import io.github.adetlo.payments.support.KafkaTestConsumer;
import io.github.adetlo.payments.support.PaymentFixtures;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** End-to-end processing through the outbox, real Kafka, the consumer and the retry scheduler. */
class PaymentFlowIT extends IntegrationTestSupport {

    private static final Duration FLOW_TIMEOUT = Duration.ofSeconds(30);

    @Autowired private AuditEntryRepository auditEntryRepository;
    @Autowired private MeterRegistry meterRegistry;

    private ApiResponse awaitStatus(String paymentId, PaymentStatus expected) {
        await().atMost(FLOW_TIMEOUT)
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(
                        () ->
                                assertThat(api.getPayment(paymentId).text("status"))
                                        .isEqualTo(expected.name()));
        return api.getPayment(paymentId);
    }

    @Test
    void receivedPaymentIsValidatedProcessedAndCompletedThroughKafka() {
        try (KafkaTestConsumer consumer =
                new KafkaTestConsumer(bootstrapServers(), KafkaTopics.PAYMENT_EVENTS)) {
            ApiResponse created = api.createPayment(PaymentFixtures.createRequest("125.50", "GBP"));
            String id = created.text("paymentId");

            ApiResponse completed = awaitStatus(id, PaymentStatus.COMPLETED);
            List<ConsumerRecord<String, String>> records =
                    consumer.awaitRecords(r -> id.equals(r.key()), 3, FLOW_TIMEOUT);

            assertThat(completed.body().get("retryCount").asInt()).isZero();
            assertThat(completed.body().get("failureReason").isNull()).isTrue();
            assertThat(records).extracting(ConsumerRecord::key).containsOnly(id);
            assertThat(records.stream().map(r -> readJson(r.value()).get("eventType").asText()))
                    .containsExactly("PaymentReceived", "PaymentValidated", "PaymentCompleted");
        }
    }

    @Test
    void kafkaEnvelopesCarryMaskedAccountsOnly() {
        try (KafkaTestConsumer consumer =
                new KafkaTestConsumer(bootstrapServers(), KafkaTopics.PAYMENT_EVENTS)) {
            ApiResponse created =
                    api.post(
                            "/api/v1/payments",
                            PaymentFixtures.createRequest("99.99", "USD"),
                            Map.of("X-Correlation-Id", "corr-kafka"));
            String id = created.text("paymentId");

            ConsumerRecord<String, String> record =
                    consumer.awaitRecord(r -> id.equals(r.key()), FLOW_TIMEOUT);
            JsonNode envelope = readJson(record.value());

            assertThat(envelope.fieldNames())
                    .toIterable()
                    .containsExactlyInAnyOrder(
                            "eventId",
                            "eventType",
                            "eventVersion",
                            "occurredAt",
                            "paymentId",
                            "correlationId",
                            "data");
            assertThat(envelope.get("eventType").asText()).isEqualTo("PaymentReceived");
            assertThat(envelope.get("eventVersion").asInt()).isEqualTo(1);
            assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-kafka");
            assertThat(envelope.get("occurredAt").asText()).endsWith("Z");
            assertThat(envelope.get("data").get("amount").asText()).isEqualTo("99.99");
            assertThat(envelope.get("data").get("currency").asText()).isEqualTo("USD");
            assertThat(envelope.get("data").get("payerAccountMasked").asText())
                    .isEqualTo("GB**...0001");
            assertThat(record.value())
                    .doesNotContain(PaymentFixtures.PAYER)
                    .doesNotContain(PaymentFixtures.PAYEE);
        }
    }

    @Test
    void settlementTimeoutRetriesThenCompletes() {
        ApiResponse created =
                api.createPaymentWithFailure(
                        PaymentFixtures.createRequest("40.00", "SGD"), "settlement-timeout");
        String id = created.text("paymentId");
        assertThat(created.text("demoFailureMode")).isEqualTo("settlement-timeout");

        awaitStatus(id, PaymentStatus.RETRY_PENDING);
        ApiResponse completed = awaitStatus(id, PaymentStatus.COMPLETED);

        assertThat(completed.body().get("retryCount").asInt()).isEqualTo(2);
        assertThat(completed.body().get("nextRetryAt").isNull()).isTrue();
        ApiResponse history = api.get("/api/v1/payments/" + id + "/events");
        List<String> events = history.body().get("events").findValuesAsText("eventType");
        assertThat(events)
                .containsExactly(
                        "PaymentReceived",
                        "PaymentValidated",
                        "PaymentProcessingStarted",
                        "PaymentRetryScheduled",
                        "PaymentProcessingStarted",
                        "PaymentRetryScheduled",
                        "PaymentProcessingStarted",
                        "PaymentCompleted");
        assertThat(history.body().get("audit").findValuesAsText("actor"))
                .contains("api", "consumer", "scheduler");
        assertThat(meterRegistry.get("payment.retry").counter().count())
                .isGreaterThanOrEqualTo(2.0);
    }

    @Test
    void settlementRejectedFailsPermanently() {
        ApiResponse created =
                api.createPaymentWithFailure(
                        PaymentFixtures.createRequest("10.00", "EUR"), "settlement-rejected");

        ApiResponse failed = awaitStatus(created.text("paymentId"), PaymentStatus.FAILED);

        assertThat(failed.text("failureReason")).contains("rejected");
        assertThat(failed.body().get("retryCount").asInt()).isZero();
        assertThat(
                        api.get("/api/v1/payments/" + failed.text("paymentId") + "/events")
                                .body()
                                .get("events")
                                .findValuesAsText("eventType"))
                .endsWith("PaymentFailed");
    }

    @Test
    void poisonMessageIsDeadLetteredAfterRetries() {
        double before = meterRegistry.get("payment.dead.letter").counter().count();
        try (KafkaTestConsumer dlt =
                new KafkaTestConsumer(bootstrapServers(), KafkaTopics.PAYMENT_EVENTS_DLT)) {
            ApiResponse created =
                    api.createPaymentWithFailure(
                            PaymentFixtures.createRequest("5.00", "GBP"), "poison-message");
            String id = created.text("paymentId");

            ConsumerRecord<String, String> record =
                    dlt.awaitRecord(r -> id.equals(r.key()), FLOW_TIMEOUT);
            ApiResponse parked = awaitStatus(id, PaymentStatus.REJECTED);

            assertThat(readJson(record.value()).get("eventType").asText())
                    .isEqualTo("PaymentReceived");
            assertThat(parked.text("failureReason"))
                    .contains("dead-lettered")
                    .contains("PoisonMessageException");
            await().atMost(FLOW_TIMEOUT)
                    .untilAsserted(
                            () ->
                                    assertThat(
                                                    auditEntryRepository
                                                            .findByPaymentIdOrderByOccurredAtAscIdAsc(
                                                                    UUID.fromString(id)))
                                            .anySatisfy(
                                                    entry ->
                                                            assertThat(entry.action())
                                                                    .isEqualTo(
                                                                            "payment.dead-lettered")));
            assertThat(meterRegistry.get("payment.dead.letter").counter().count())
                    .isGreaterThan(before);
            assertThat(
                            api.get("/api/v1/system/stats")
                                    .body()
                                    .get("kafka")
                                    .get("deadLetterTotal")
                                    .asLong())
                    .isPositive();
        }
    }

    @Test
    void businessValidationRejectsPayerEqualToPayeeAndAmountsOverTheLimit() {
        Map<String, Object> self = PaymentFixtures.createRequest();
        self.put("payeeAccount", PaymentFixtures.PAYER);
        ApiResponse selfPayment = api.createPayment(self);
        ApiResponse tooLarge =
                api.createPayment(PaymentFixtures.createRequest("1000000.01", "GBP"));

        ApiResponse rejectedSelf =
                awaitStatus(selfPayment.text("paymentId"), PaymentStatus.REJECTED);
        ApiResponse rejectedLarge = awaitStatus(tooLarge.text("paymentId"), PaymentStatus.REJECTED);

        assertThat(rejectedSelf.text("failureReason")).contains("must differ");
        assertThat(rejectedLarge.text("failureReason")).contains("limit");
        assertThat(
                        api.get("/api/v1/payments/" + rejectedSelf.text("paymentId") + "/events")
                                .body()
                                .get("events")
                                .findValuesAsText("eventType"))
                .containsExactly("PaymentReceived", "PaymentRejected");
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Invalid JSON on Kafka: " + json, e);
        }
    }
}
