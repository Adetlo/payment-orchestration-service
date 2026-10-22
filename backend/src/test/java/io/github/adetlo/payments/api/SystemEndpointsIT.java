package io.github.adetlo.payments.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.adetlo.payments.support.ApiClient.ApiResponse;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.util.List;
import org.junit.jupiter.api.Test;

class SystemEndpointsIT extends IntegrationTestSupport {

    @Test
    void systemHealthReportsEveryComponentUp() {
        ApiResponse response = api.get("/api/v1/system/health");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text("status")).isEqualTo("UP");
        JsonNode components = response.body().get("components");
        assertThat(components.fieldNames())
                .toIterable()
                .containsExactly("api", "postgresql", "kafka", "redis");
        for (String name : List.of("api", "postgresql", "kafka", "redis")) {
            assertThat(components.get(name).get("status").asText()).as(name).isEqualTo("UP");
            assertThat(components.get(name).get("detail").asText()).isNotBlank();
        }
    }

    @Test
    void systemStatsHasTheDesignShape() {
        api.createPayment(PaymentFixtures.createRequest());

        ApiResponse response = api.get("/api/v1/system/stats");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().fieldNames())
                .toIterable()
                .containsExactly("payments", "outbox", "kafka", "reconciliation");
        JsonNode payments = response.body().get("payments");
        assertThat(payments.get("total").asLong()).isPositive();
        assertThat(payments.get("byStatus").fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "RECEIVED",
                        "VALIDATED",
                        "PROCESSING",
                        "COMPLETED",
                        "REJECTED",
                        "FAILED",
                        "RETRY_PENDING",
                        "CANCELLED");
        long sum = 0;
        for (JsonNode n : payments.get("byStatus")) {
            sum += n.asLong();
        }
        assertThat(sum).isEqualTo(payments.get("total").asLong());
        assertThat(response.body().get("outbox").fieldNames())
                .toIterable()
                .containsExactly("pending", "failed", "published");
        assertThat(response.body().get("kafka").fieldNames())
                .toIterable()
                .containsExactly("publishedTotal", "deadLetterTotal");
        assertThat(response.body().get("reconciliation").has("lastRun")).isTrue();
    }

    @Test
    void actuatorHealthAndReadinessAreUp() throws Exception {
        ApiResponse health = api.get("/actuator/health");
        ApiResponse readiness = api.get("/actuator/health/readiness");

        assertThat(health.status()).isEqualTo(200);
        assertThat(health.text("status")).isEqualTo("UP");
        assertThat(health.body().get("components").get("kafka").get("status").asText())
                .isEqualTo("UP");
        assertThat(health.body().get("components").get("redis").get("status").asText())
                .isEqualTo("UP");
        assertThat(health.body().get("components").get("db").get("status").asText())
                .isEqualTo("UP");
        assertThat(readiness.status()).isEqualTo(200);
        assertThat(readiness.text("status")).isEqualTo("UP");
    }

    @Test
    void prometheusExposesEveryBusinessMetric() throws Exception {
        api.createPayment(PaymentFixtures.createRequest());
        String scrape =
                mockMvc.perform(
                                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                        .get("/actuator/prometheus"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(scrape)
                .contains("payment_received_total")
                .contains("payment_completed_total")
                .contains("payment_failed_total")
                .contains("payment_rejected_total")
                .contains("payment_cancelled_total")
                .contains("payment_retry_total")
                .contains("payment_dead_letter_total")
                .contains("payment_processing_duration_seconds")
                .contains("outbox_pending ")
                .contains("outbox_failed ")
                .contains("outbox_published_total")
                .contains("reconciliation_runs_total")
                .contains("reconciliation_mismatch_total{")
                .contains("classification=\"AMOUNT_MISMATCH\"")
                .contains("idempotency_replay_total")
                .contains("idempotency_conflict_total");
    }

    @Test
    void openApiDocumentIsServed() {
        ApiResponse docs = api.get("/v3/api-docs");

        assertThat(docs.status()).isEqualTo(200);
        assertThat(docs.body().get("paths").fieldNames())
                .toIterable()
                .contains(
                        "/api/v1/payments",
                        "/api/v1/payments/{paymentId}/cancel",
                        "/api/v1/reconciliation/run",
                        "/api/v1/system/stats");
    }
}
