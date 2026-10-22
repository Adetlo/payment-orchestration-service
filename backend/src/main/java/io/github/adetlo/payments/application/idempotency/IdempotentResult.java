package io.github.adetlo.payments.application.idempotency;

import java.util.UUID;

/** Outcome of an idempotent create: the stored/computed response and whether it was replayed. */
public record IdempotentResult(int status, String body, UUID paymentId, boolean replayed) {}
