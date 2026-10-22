package io.github.adetlo.payments.domain;

import java.util.UUID;

/** Raised when a payment is asked to move to a status the state machine does not allow. */
public class InvalidStateTransitionException extends RuntimeException {

    private final UUID paymentId;
    private final PaymentStatus from;
    private final PaymentStatus to;

    public InvalidStateTransitionException(UUID paymentId, PaymentStatus from, PaymentStatus to) {
        super("Payment " + paymentId + " cannot move from " + from + " to " + to);
        this.paymentId = paymentId;
        this.from = from;
        this.to = to;
    }

    public UUID paymentId() {
        return paymentId;
    }

    public PaymentStatus from() {
        return from;
    }

    public PaymentStatus to() {
        return to;
    }
}
