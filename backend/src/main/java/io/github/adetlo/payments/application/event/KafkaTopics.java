package io.github.adetlo.payments.application.event;

/** Topic names shared by the publisher, the consumer and the tests. */
public final class KafkaTopics {

    public static final String PAYMENT_EVENTS = "payment.events.v1";
    public static final String PAYMENT_EVENTS_DLT = "payment.events.v1.dlt";
    public static final String RECONCILIATION_EVENTS = "reconciliation.events.v1";

    private KafkaTopics() {}
}
