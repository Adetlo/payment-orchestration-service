package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adetlo.payments.application.event.EventEnvelope;
import io.github.adetlo.payments.application.event.EventEnvelopeFactory;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentEventType;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class EventEnvelopeFactoryTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final EventEnvelopeFactory factory = new EventEnvelopeFactory(objectMapper);

    @Test
    void envelopeMatchesTheDesignShape() throws Exception {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.COMPLETED);
        UUID eventId = UUID.randomUUID();
        Instant at = Instant.parse("2026-10-22T10:15:30.123Z");
        Map<String, Object> data =
                factory.paymentData(payment, PaymentStatus.PROCESSING, null, Map.of());

        String json =
                factory.toJson(
                        factory.envelope(
                                eventId,
                                PaymentEventType.PaymentCompleted,
                                payment.paymentId(),
                                "corr-1",
                                at,
                                data));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(node.get("eventType").asText()).isEqualTo("PaymentCompleted");
        assertThat(node.get("eventVersion").asInt()).isEqualTo(1);
        assertThat(node.get("occurredAt").asText()).isEqualTo("2026-10-22T10:15:30.123Z");
        assertThat(node.get("paymentId").asText()).isEqualTo(payment.paymentId().toString());
        assertThat(node.get("correlationId").asText()).isEqualTo("corr-1");
        JsonNode body = node.get("data");
        assertThat(body.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(body.get("amount").asText()).isEqualTo("125.50");
        assertThat(body.get("currency").asText()).isEqualTo("GBP");
        assertThat(body.get("reference").asText()).isEqualTo("INV-1001");
        assertThat(body.get("fromStatus").asText()).isEqualTo("PROCESSING");
        assertThat(body.get("retryCount").asInt()).isZero();
        assertThat(body.get("failureReason").isNull()).isTrue();
    }

    @Test
    void accountNumbersNeverAppearInEvents() {
        Payment payment = PaymentFixtures.payment();
        Map<String, Object> data = factory.paymentData(payment, null, "why", Map.of("extra", "x"));
        String json = factory.toJson(data);

        assertThat(json)
                .doesNotContain(PaymentFixtures.PAYER)
                .doesNotContain(PaymentFixtures.PAYEE);
        assertThat(data)
                .containsEntry("payerAccountMasked", "GB**...0001")
                .containsEntry("payeeAccountMasked", "GB**...0002");
        assertThat(data).containsEntry("reason", "why").containsEntry("extra", "x");
    }

    @Test
    void parseRoundTripsAndRejectsGarbage() {
        EventEnvelope envelope =
                factory.envelope(
                        UUID.randomUUID(),
                        PaymentEventType.PaymentReceived,
                        UUID.randomUUID(),
                        "c",
                        Instant.now(),
                        Map.of("k", 1));

        EventEnvelope parsed = factory.parse(factory.toJson(envelope));

        assertThat(parsed.eventId()).isEqualTo(envelope.eventId());
        assertThat(parsed.paymentId()).isEqualTo(envelope.paymentId());
        assertThat(parsed.data()).containsEntry("k", 1);
        assertThatThrownBy(() -> factory.parse("not json"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
