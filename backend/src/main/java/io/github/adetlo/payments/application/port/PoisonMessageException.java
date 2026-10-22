package io.github.adetlo.payments.application.port;

import java.util.UUID;

/**
 * Thrown by the consumer for payments flagged with {@code poison-message} so the record
 * dead-letters.
 */
public class PoisonMessageException extends RuntimeException {

    public PoisonMessageException(UUID paymentId) {
        super("Poison message injected for payment " + paymentId);
    }
}
