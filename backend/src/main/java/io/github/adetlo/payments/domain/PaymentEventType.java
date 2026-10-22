package io.github.adetlo.payments.domain;

/**
 * Event types recorded in the payment history. Constants are named exactly as they appear on the
 * wire so that the database, the Kafka envelope and the API agree without a mapping layer.
 */
public enum PaymentEventType {
    PaymentReceived(true),
    PaymentValidated(true),
    PaymentRejected(true),
    /** Internal transition into PROCESSING; recorded but not published to Kafka. */
    PaymentProcessingStarted(false),
    PaymentRetryScheduled(true),
    PaymentCompleted(true),
    PaymentFailed(true),
    PaymentCancelled(true),
    ReconciliationCompleted(true);

    private final boolean published;

    PaymentEventType(boolean published) {
        this.published = published;
    }

    /** Whether this event is externally visible, i.e. written to the outbox. */
    public boolean isPublished() {
        return published;
    }

    /** The event emitted when a payment enters the given status. */
    public static PaymentEventType forTarget(PaymentStatus target) {
        return switch (target) {
            case RECEIVED -> PaymentReceived;
            case VALIDATED -> PaymentValidated;
            case REJECTED -> PaymentRejected;
            case PROCESSING -> PaymentProcessingStarted;
            case RETRY_PENDING -> PaymentRetryScheduled;
            case COMPLETED -> PaymentCompleted;
            case FAILED -> PaymentFailed;
            case CANCELLED -> PaymentCancelled;
        };
    }
}
