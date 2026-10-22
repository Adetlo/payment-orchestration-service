package io.github.adetlo.payments.api.dto;

import java.util.List;
import java.util.UUID;

/** Body of {@code GET /api/v1/payments/{id}/events}. */
public record PaymentHistoryResponse(
        UUID paymentId, List<PaymentEventResponse> events, List<AuditEntryResponse> audit) {

    public PaymentHistoryResponse {
        events = List.copyOf(events);
        audit = List.copyOf(audit);
    }
}
