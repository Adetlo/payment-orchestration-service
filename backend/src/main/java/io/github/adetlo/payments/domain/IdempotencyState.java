package io.github.adetlo.payments.domain;

/** Progress of a request identified by an idempotency key. */
public enum IdempotencyState {
    IN_PROGRESS,
    COMPLETED
}
