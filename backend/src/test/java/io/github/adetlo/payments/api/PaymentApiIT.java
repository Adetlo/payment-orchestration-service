package io.github.adetlo.payments.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.persistence.PaymentRepository;
import io.github.adetlo.payments.support.ApiClient.ApiResponse;
import io.github.adetlo.payments.support.IntegrationTestSupport;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaymentApiIT extends IntegrationTestSupport {

    @Autowired private PaymentRepository paymentRepository;

    @Test
    void createReturns201WithLocationAndTracingHeaders() {
        Map<String, Object> body = PaymentFixtures.createRequest("250.00", "EUR");

        ApiResponse response =
                api.post("/api/v1/payments", body, Map.of("X-Correlation-Id", "corr-api-1"));

        assertThat(response.status()).isEqualTo(201);
        assertThat(response.header("Location"))
                .isEqualTo("/api/v1/payments/" + response.text("paymentId"));
        assertThat(response.header("X-Request-Id")).isNotBlank();
        assertThat(response.header("X-Correlation-Id")).isEqualTo("corr-api-1");
        assertThat(response.header("Cache-Control")).isEqualTo("no-store");
        assertThat(response.header("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(response.body().fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "paymentId",
                        "status",
                        "payerAccount",
                        "payeeAccount",
                        "amount",
                        "currency",
                        "reference",
                        "failureReason",
                        "retryCount",
                        "maxRetries",
                        "nextRetryAt",
                        "idempotencyKey",
                        "correlationId",
                        "demoFailureMode",
                        "createdAt",
                        "updatedAt");
        assertThat(response.text("status")).isEqualTo("RECEIVED");
        assertThat(response.text("amount")).isEqualTo("250.00");
        assertThat(response.text("currency")).isEqualTo("EUR");
        assertThat(response.text("correlationId")).isEqualTo("corr-api-1");
        assertThat(response.body().get("maxRetries").asInt()).isEqualTo(3);
        assertThat(response.body().get("idempotencyKey").isNull()).isTrue();
        assertThat(response.body().get("demoFailureMode").isNull()).isTrue();
    }

    @Test
    void getReturnsPaymentOr404Problem() {
        ApiResponse created = api.createPayment(PaymentFixtures.createRequest());
        ApiResponse found = api.getPayment(created.text("paymentId"));
        ApiResponse missing = api.getPayment(UUID.randomUUID().toString());
        ApiResponse badId = api.getPayment("not-a-uuid");

        assertThat(found.status()).isEqualTo(200);
        assertThat(found.text("paymentId")).isEqualTo(created.text("paymentId"));
        assertThat(missing.status()).isEqualTo(404);
        assertThat(missing.header("Content-Type")).startsWith("application/problem+json");
        assertThat(missing.text("type")).isEqualTo("about:blank#not-found");
        assertThat(missing.header("X-Request-Id")).isNotBlank();
        assertThat(badId.status()).isEqualTo(400);
    }

    @Test
    void listFiltersByStatusCurrencyAndDateAndIsNewestFirst() {
        Payment completed =
                paymentRepository.save(
                        PaymentFixtures.paymentIn(PaymentStatus.COMPLETED, "7", "JPY"));
        Payment cancelled =
                paymentRepository.save(
                        PaymentFixtures.paymentIn(PaymentStatus.CANCELLED, "8", "JPY"));

        ApiResponse byCurrency = api.get("/api/v1/payments?currency=JPY&size=50");
        ApiResponse byStatus = api.get("/api/v1/payments?currency=jpy&status=CANCELLED&size=50");
        ApiResponse future =
                api.get("/api/v1/payments?from=" + Instant.now().plusSeconds(3600) + "&size=5");
        ApiResponse badStatus = api.get("/api/v1/payments?status=NOPE");
        ApiResponse badSize = api.get("/api/v1/payments?size=0");

        assertThat(byCurrency.status()).isEqualTo(200);
        assertThat(byCurrency.body().fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("items", "page", "size", "totalElements", "totalPages");
        assertThat(byCurrency.body().get("items").findValuesAsText("paymentId"))
                .contains(completed.paymentId().toString(), cancelled.paymentId().toString());
        assertThat(byCurrency.body().get("items").findValuesAsText("currency")).containsOnly("JPY");
        assertThat(byStatus.body().get("items").findValuesAsText("status"))
                .containsOnly("CANCELLED");
        assertThat(byStatus.body().get("items").findValuesAsText("paymentId"))
                .contains(cancelled.paymentId().toString());
        assertThat(future.body().get("totalElements").asLong()).isZero();
        assertThat(future.body().get("size").asInt()).isEqualTo(5);
        JsonNode items = byCurrency.body().get("items");
        for (int i = 1; i < items.size(); i++) {
            assertThat(Instant.parse(items.get(i - 1).get("createdAt").asText()))
                    .isAfterOrEqualTo(Instant.parse(items.get(i).get("createdAt").asText()));
        }
        assertThat(badStatus.status()).isEqualTo(400);
        assertThat(badSize.status()).isEqualTo(400);
        assertThat(badSize.text("type")).isEqualTo("about:blank#validation-error");
    }

    @Test
    void validationFailuresProduceProblemWithFieldErrors() {
        Map<String, Object> body = PaymentFixtures.createRequest();
        body.put("payerAccount", "nope");
        body.put("amount", "-5");
        body.put("reference", "");

        ApiResponse response = api.createPayment(body);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.header("Content-Type")).startsWith("application/problem+json");
        assertThat(response.text("type")).isEqualTo("about:blank#validation-error");
        assertThat(response.text("title")).isEqualTo("Validation failed");
        assertThat(response.body().get("status").asInt()).isEqualTo(400);
        assertThat(response.body().get("instance").asText()).isEqualTo("/api/v1/payments");
        assertThat(response.body().get("errors").findValuesAsText("field"))
                .contains("payerAccount", "amount", "reference");
        assertThat(response.raw())
                .doesNotContain("trace")
                .doesNotContain("Exception")
                .doesNotContain("at io.github");
    }

    @Test
    void domainRulesProduce400ForCurrencyScaleAndUnsupportedCurrency() {
        ApiResponse wrongScale = api.createPayment(PaymentFixtures.createRequest("10.005", "GBP"));
        ApiResponse jpyScale = api.createPayment(PaymentFixtures.createRequest("10.5", "JPY"));
        ApiResponse unsupported = api.createPayment(PaymentFixtures.createRequest("10.00", "CHF"));
        ApiResponse zero = api.createPayment(PaymentFixtures.createRequest("0.00", "GBP"));

        assertThat(wrongScale.status()).isEqualTo(400);
        assertThat(wrongScale.body().get("errors").get(0).get("field").asText())
                .isEqualTo("amount");
        assertThat(jpyScale.status()).isEqualTo(400);
        assertThat(unsupported.status()).isEqualTo(400);
        assertThat(unsupported.body().get("errors").get(0).get("field").asText())
                .isEqualTo("currency");
        assertThat(zero.status()).isEqualTo(400);
        assertThat(zero.body().get("errors").get(0).get("message").asText())
                .contains("greater than zero");
    }

    @Test
    void malformedJsonAndUnknownRoutesAreProblemsWithoutStackTraces() {
        ApiResponse malformed = api.post("/api/v1/payments", "{\"payerAccount\": ");
        ApiResponse unknown = api.get("/api/v1/nothing-here");

        assertThat(malformed.status()).isEqualTo(400);
        assertThat(malformed.text("type")).isEqualTo("about:blank#malformed-request");
        assertThat(malformed.raw()).doesNotContain("trace").doesNotContain("com.fasterxml");
        assertThat(unknown.status()).isEqualTo(404);
        assertThat(unknown.header("Content-Type")).startsWith("application/problem+json");
        assertThat(unknown.raw()).doesNotContain("trace");
    }

    @Test
    void unknownDemoFailureIsRejectedWhenInjectionIsEnabled() {
        ApiResponse response =
                api.createPaymentWithFailure(PaymentFixtures.createRequest(), "meltdown");

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("errors").get(0).get("field").asText())
                .isEqualTo("X-Demo-Failure");
    }

    @Test
    void cancelIsAllowedFromRetryPendingAndForbiddenWhenCompleted() {
        Payment retryPending = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        retryPending.transitionTo(
                PaymentStatus.RETRY_PENDING, Instant.now().plusSeconds(3600), "slow");
        paymentRepository.save(retryPending);
        Payment completed =
                paymentRepository.save(PaymentFixtures.paymentIn(PaymentStatus.COMPLETED));

        ApiResponse cancelled =
                api.cancelPayment(retryPending.paymentId().toString(), "customer changed mind");
        ApiResponse forbidden = api.cancelPayment(completed.paymentId().toString(), null);
        ApiResponse missing = api.cancelPayment(UUID.randomUUID().toString(), null);

        assertThat(cancelled.status()).isEqualTo(200);
        assertThat(cancelled.text("status")).isEqualTo("CANCELLED");
        assertThat(cancelled.text("failureReason")).isEqualTo("customer changed mind");
        assertThat(cancelled.body().get("nextRetryAt").isNull()).isTrue();
        assertThat(forbidden.status()).isEqualTo(409);
        assertThat(forbidden.text("type")).isEqualTo("about:blank#invalid-state-transition");
        assertThat(forbidden.text("fromStatus")).isEqualTo("COMPLETED");
        assertThat(forbidden.text("toStatus")).isEqualTo("CANCELLED");
        assertThat(missing.status()).isEqualTo(404);
        ApiResponse history = api.get("/api/v1/payments/" + retryPending.paymentId() + "/events");
        assertThat(history.body().get("events").findValuesAsText("eventType"))
                .containsExactly("PaymentCancelled");
        assertThat(history.body().get("audit").findValuesAsText("action"))
                .containsExactly("payment.cancelled");
        assertThat(history.body().get("audit").get(0).get("actor").asText()).isEqualTo("api");
    }

    @Test
    void eventsEndpointReturnsTimelineOldestFirst() {
        ApiResponse created = api.createPayment(PaymentFixtures.createRequest());
        String id = created.text("paymentId");

        ApiResponse history = api.get("/api/v1/payments/" + id + "/events");
        ApiResponse missing = api.get("/api/v1/payments/" + UUID.randomUUID() + "/events");

        assertThat(history.status()).isEqualTo(200);
        assertThat(history.text("paymentId")).isEqualTo(id);
        JsonNode first = history.body().get("events").get(0);
        assertThat(first.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder(
                        "eventId",
                        "eventType",
                        "eventVersion",
                        "fromStatus",
                        "toStatus",
                        "occurredAt",
                        "details");
        assertThat(first.get("eventType").asText()).isEqualTo("PaymentReceived");
        assertThat(first.get("fromStatus").isNull()).isTrue();
        assertThat(first.get("toStatus").asText()).isEqualTo("RECEIVED");
        assertThat(first.get("details").get("payerAccountMasked").asText())
                .isEqualTo("GB**...0001");
        JsonNode audit = history.body().get("audit").get(0);
        assertThat(audit.fieldNames())
                .toIterable()
                .containsExactlyInAnyOrder("id", "action", "actor", "details", "occurredAt");
        assertThat(audit.get("action").asText()).isEqualTo("payment.created");
        assertThat(missing.status()).isEqualTo(404);
    }
}
