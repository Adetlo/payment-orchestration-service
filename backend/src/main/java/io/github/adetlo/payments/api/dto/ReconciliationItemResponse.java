package io.github.adetlo.payments.api.dto;

import io.github.adetlo.payments.domain.ReconciliationItem;
import java.util.UUID;

/** One reconciliation item. */
public record ReconciliationItemResponse(
        Long id,
        UUID paymentId,
        String externalReference,
        String classification,
        String internalAmount,
        String externalAmount,
        String internalStatus,
        String externalStatus,
        String details) {

    public static ReconciliationItemResponse from(ReconciliationItem item) {
        return new ReconciliationItemResponse(
                item.id(),
                item.paymentId(),
                item.externalReference(),
                item.classification().name(),
                item.internalAmount() == null ? null : item.internalAmount().toPlainString(),
                item.externalAmount() == null ? null : item.externalAmount().toPlainString(),
                item.internalStatus(),
                item.externalStatus(),
                item.details());
    }
}
