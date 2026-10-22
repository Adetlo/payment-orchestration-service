package io.github.adetlo.payments.application.idempotency;

/** Another request with the same key is still running; mapped to HTTP 409 with Retry-After. */
public class IdempotencyInProgressException extends RuntimeException {

    public IdempotencyInProgressException(String key) {
        super("A request with Idempotency-Key '" + key + "' is still in progress");
    }
}
