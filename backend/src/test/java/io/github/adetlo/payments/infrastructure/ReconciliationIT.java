package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.adetlo.payments.application.event.KafkaTopics;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.AuditEntryRepository;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.support.ApiClient.ApiResponse;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import io.github.adetlo.payments.support.KafkaTestConsumer;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ReconciliationIT extends IntegrationTestSupport {

    @Autowired private PaymentRepository paymentRepository;
    @Autowired private AuditEntryRepository auditEntryRepository;

    @BeforeEach
    void seedTerminalPayments() {
        paymentRepository.saveAll(
                List.of(
                        PaymentFixtures.paymentIn(PaymentStatus.COMPLETED, "10.00", "GBP"),
                        PaymentFixtures.paymentIn(PaymentStatus.COMPLETED, "20.00", "GBP"),
                        PaymentFixtures.paymentIn(PaymentStatus.COMPLETED, "30.00", "GBP"),
                        PaymentFixtures.paymentIn(PaymentStatus.FAILED, "40.00", "GBP")));
    }

    @Test
    void cleanScenarioMatchesEverything() {
        long auditBefore = auditEntryRepository.countByAction("reconciliation.completed");

        ApiResponse response = api.post("/api/v1/reconciliation/run", Map.of("scenario", "clean"));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().fieldNames())
                .toIterable()
                .containsExactly(
                        "runId",
                        "scenario",
                        "status",
                        "startedAt",
                        "completedAt",
                        "recordsChecked",
                        "matched",
                        "unmatched",
                        "byClassification",
                        "items");
        assertThat(response.text("scenario")).isEqualTo("clean");
        assertThat(response.text("status")).isEqualTo("COMPLETED");
        assertThat(response.body().get("recordsChecked").asInt()).isGreaterThanOrEqualTo(4);
        assertThat(response.body().get("unmatched").asInt()).isZero();
        assertThat(response.body().get("matched").asInt())
                .isEqualTo(response.body().get("recordsChecked").asInt());
        JsonNode by = response.body().get("byClassification");
        assertThat(by.fieldNames())
                .toIterable()
                .containsExactly(
                        "MATCHED",
                        "AMOUNT_MISMATCH",
                        "MISSING_INTERNAL",
                        "MISSING_EXTERNAL",
                        "STATUS_MISMATCH");
        assertThat(by.get("AMOUNT_MISMATCH").asInt()).isZero();
        assertThat(response.body().get("items").findValuesAsText("classification"))
                .containsOnly("MATCHED");
        assertThat(auditEntryRepository.countByAction("reconciliation.completed"))
                .isEqualTo(auditBefore + 1);
    }

    @Test
    void discrepancyScenarioClassifiesOneOfEachKind() {
        try (KafkaTestConsumer consumer =
                new KafkaTestConsumer(bootstrapServers(), KafkaTopics.RECONCILIATION_EVENTS)) {
            ApiResponse response =
                    api.post(
                            "/api/v1/reconciliation/run",
                            Map.of("scenario", "with-discrepancies", "seed", 42));

            assertThat(response.status()).isEqualTo(200);
            JsonNode by = response.body().get("byClassification");
            assertThat(by.get("AMOUNT_MISMATCH").asInt()).isEqualTo(1);
            assertThat(by.get("MISSING_INTERNAL").asInt()).isEqualTo(1);
            assertThat(by.get("MISSING_EXTERNAL").asInt()).isEqualTo(1);
            assertThat(by.get("STATUS_MISMATCH").asInt()).isEqualTo(1);
            assertThat(response.body().get("unmatched").asInt()).isEqualTo(4);
            assertThat(by.get("MATCHED").asInt())
                    .isEqualTo(response.body().get("recordsChecked").asInt() - 4);
            JsonNode item = response.body().get("items").get(0);
            assertThat(item.fieldNames())
                    .toIterable()
                    .containsExactly(
                            "id",
                            "paymentId",
                            "externalReference",
                            "classification",
                            "internalAmount",
                            "externalAmount",
                            "internalStatus",
                            "externalStatus",
                            "details");

            String runId = response.text("runId");
            ConsumerRecord<String, String> record =
                    consumer.awaitRecord(r -> runId.equals(r.key()), Duration.ofSeconds(30));
            JsonNode envelope = readJson(record.value());
            assertThat(envelope.get("eventType").asText()).isEqualTo("ReconciliationCompleted");
            assertThat(envelope.get("paymentId").isNull()).isTrue();
            assertThat(envelope.get("data").get("byClassification").get("AMOUNT_MISMATCH").asInt())
                    .isEqualTo(1);
        }
    }

    @Test
    void runsCanBeListedAndFetchedWithItems() {
        ApiResponse run =
                api.post("/api/v1/reconciliation/run", Map.of("scenario", "with-discrepancies"));
        String runId = run.text("runId");

        ApiResponse detail = api.get("/api/v1/reconciliation/runs/" + runId);
        ApiResponse list = api.get("/api/v1/reconciliation/runs?page=0&size=5");
        ApiResponse missing = api.get("/api/v1/reconciliation/runs/" + UUID.randomUUID());

        assertThat(detail.status()).isEqualTo(200);
        assertThat(detail.text("runId")).isEqualTo(runId);
        assertThat(detail.body().get("items").size())
                .isEqualTo(detail.body().get("recordsChecked").asInt());
        assertThat(list.status()).isEqualTo(200);
        assertThat(list.body().get("items").get(0).get("runId").asText()).isEqualTo(runId);
        assertThat(list.body().get("items").get(0).has("items")).isFalse();
        assertThat(list.body().get("size").asInt()).isEqualTo(5);
        assertThat(missing.status()).isEqualTo(404);
        assertThat(
                        api.get("/api/v1/system/stats")
                                .body()
                                .get("reconciliation")
                                .get("lastRun")
                                .get("runId")
                                .asText())
                .isEqualTo(runId);
    }

    @Test
    void defaultsToCleanAndRejectsUnknownScenario() {
        ApiResponse noBody = api.post("/api/v1/reconciliation/run", null);
        ApiResponse unknown = api.post("/api/v1/reconciliation/run", Map.of("scenario", "chaos"));

        assertThat(noBody.status()).isEqualTo(200);
        assertThat(noBody.text("scenario")).isEqualTo("clean");
        assertThat(unknown.status()).isEqualTo(400);
        assertThat(unknown.body().get("errors").get(0).get("field").asText()).isEqualTo("scenario");
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Invalid JSON on Kafka: " + json, e);
        }
    }
}
