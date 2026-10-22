package io.github.adetlo.payments.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** One audit entry. */
public record AuditEntryResponse(
        Long id, String action, String actor, JsonNode details, Instant occurredAt) {}
