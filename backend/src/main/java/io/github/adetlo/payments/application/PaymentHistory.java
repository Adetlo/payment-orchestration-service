package io.github.adetlo.payments.application;

import io.github.adetlo.payments.domain.AuditEntry;
import io.github.adetlo.payments.domain.PaymentEvent;
import java.util.List;
import java.util.UUID;

/** Timeline of a payment: its events and audit entries, oldest first. */
public record PaymentHistory(UUID paymentId, List<PaymentEvent> events, List<AuditEntry> audit) {

    public PaymentHistory {
        events = List.copyOf(events);
        audit = List.copyOf(audit);
    }
}
