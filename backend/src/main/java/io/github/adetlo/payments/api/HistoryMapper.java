package io.github.adetlo.payments.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.adetlo.payments.api.dto.AuditEntryResponse;
import io.github.adetlo.payments.api.dto.PaymentEventResponse;
import io.github.adetlo.payments.api.dto.PaymentHistoryResponse;
import io.github.adetlo.payments.application.PaymentHistory;
import io.github.adetlo.payments.domain.AuditEntry;
import io.github.adetlo.payments.domain.PaymentEvent;
import org.springframework.stereotype.Component;

/** Maps history entities to their response shapes, parsing stored JSON details into objects. */
@Component
public class HistoryMapper {

    private final ObjectMapper objectMapper;

    public HistoryMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PaymentHistoryResponse toResponse(PaymentHistory history) {
        return new PaymentHistoryResponse(
                history.paymentId(),
                history.events().stream().map(this::toResponse).toList(),
                history.audit().stream().map(this::toResponse).toList());
    }

    PaymentEventResponse toResponse(PaymentEvent event) {
        return new PaymentEventResponse(
                event.eventId(),
                event.eventType().name(),
                event.eventVersion(),
                event.fromStatus() == null ? null : event.fromStatus().name(),
                event.toStatus().name(),
                event.occurredAt(),
                json(event.details()));
    }

    AuditEntryResponse toResponse(AuditEntry entry) {
        return new AuditEntryResponse(
                entry.id(),
                entry.action(),
                entry.actor().wireName(),
                json(entry.details()),
                entry.occurredAt());
    }

    private JsonNode json(String raw) {
        if (raw == null || raw.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(raw);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode().put("raw", raw);
        }
    }
}
