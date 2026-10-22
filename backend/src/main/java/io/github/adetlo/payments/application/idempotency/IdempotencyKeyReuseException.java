package io.github.adetlo.payments.application.idempotency;

/** The key was already used with a different request body; mapped to HTTP 409. */
public class IdempotencyKeyReuseException extends RuntimeException {

    public IdempotencyKeyReuseException(String key) {
        super("Idempotency-Key '" + key + "' was already used with a different request payload");
    }
}
