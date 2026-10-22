package io.github.adetlo.payments.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentEvent;
import io.github.adetlo.payments.domain.PaymentEventType;
import io.github.adetlo.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds event envelopes and (de)serialises them; account numbers are only ever masked. */
@Component
public class EventEnvelopeFactory {

    private final ObjectMapper objectMapper;

    public EventEnvelopeFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** The {@code data} section describing a payment after a transition. */
    public Map<String, Object> paymentData(
            Payment payment, PaymentStatus fromStatus, String reason, Map<String, Object> extra) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", payment.status().name());
        data.put("amount", payment.money().amount().toPlainString());
        data.put("currency", payment.money().currencyCode());
        data.put("reference", payment.instruction().reference());
        data.put("fromStatus", fromStatus == null ? null : fromStatus.name());
        data.put("retryCount", payment.retryCount());
        data.put("failureReason", payment.failureReason());
        data.put("payerAccountMasked", payment.instruction().payerAccount().masked());
        data.put("payeeAccountMasked", payment.instruction().payeeAccount().masked());
        if (payment.nextRetryAt() != null) {
            data.put("nextRetryAt", payment.nextRetryAt().toString());
        }
        if (reason != null) {
            data.put("reason", reason);
        }
        if (extra != null) {
            data.putAll(extra);
        }
        return data;
    }

    public EventEnvelope envelope(
            UUID eventId,
            PaymentEventType eventType,
            UUID paymentId,
            String correlationId,
            Instant occurredAt,
            Map<String, Object> data) {
        return new EventEnvelope(
                eventId,
                eventType.name(),
                PaymentEvent.CURRENT_VERSION,
                occurredAt,
                paymentId,
                correlationId,
                data);
    }

    public String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Cannot serialise " + value.getClass().getSimpleName(), e);
        }
    }

    public EventEnvelope parse(String json) {
        try {
            return objectMapper.readValue(json, EventEnvelope.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Malformed event envelope", e);
        }
    }
}
