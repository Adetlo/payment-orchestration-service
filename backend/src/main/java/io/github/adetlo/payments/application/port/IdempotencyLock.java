package io.github.adetlo.payments.application.port;

/**
 * Short-lived lock that serialises concurrent requests carrying the same idempotency key before
 * they reach the database constraint. Implementations must fail safe: when the lock store is
 * unavailable they return {@code true} and let the database constraint decide.
 */
public interface IdempotencyLock {

    boolean tryAcquire(String key);

    void release(String key);
}
