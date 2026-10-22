package io.github.adetlo.payments.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** One history event of a payment. */
public record PaymentEventResponse(
        UUID eventId,
        String eventType,
        int eventVersion,
        String fromStatus,
        String toStatus,
        Instant occurredAt,
        JsonNode details) {}
