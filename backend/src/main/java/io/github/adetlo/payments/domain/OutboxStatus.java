package io.github.adetlo.payments.domain;

/** Delivery state of an outbox row. */
public enum OutboxStatus {
    NEW,
    PUBLISHED,
    FAILED
}
