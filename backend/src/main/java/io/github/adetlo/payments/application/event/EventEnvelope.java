package io.github.adetlo.payments.application.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Wire format of every event on {@code payment.events.v1} and {@code reconciliation.events.v1}. */
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({
    "eventId",
    "eventType",
    "eventVersion",
    "occurredAt",
    "paymentId",
    "correlationId",
    "data"
})
public record EventEnvelope(
        UUID eventId,
        String eventType,
        int eventVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        UUID paymentId,
        String correlationId,
        Map<String, Object> data) {

    public EventEnvelope {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }
}
