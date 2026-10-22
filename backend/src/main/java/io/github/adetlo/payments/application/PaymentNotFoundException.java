package io.github.adetlo.payments.application;

import java.util.UUID;

/** Raised when a payment id is unknown; mapped to HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID paymentId) {
        super("Payment " + paymentId + " not found");
    }
}
