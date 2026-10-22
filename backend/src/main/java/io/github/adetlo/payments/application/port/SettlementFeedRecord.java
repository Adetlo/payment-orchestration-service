package io.github.adetlo.payments.application.port;

import java.math.BigDecimal;
import java.util.UUID;

/** One line of the external settlement statement. */
public record SettlementFeedRecord(
        String externalReference,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        String status) {

    public static final String STATUS_SETTLED = "SETTLED";
    public static final String STATUS_FAILED = "FAILED";
}
