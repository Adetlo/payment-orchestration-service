package io.github.adetlo.payments.application.port;

import java.time.Duration;

/** Fixed-window request limiter keyed by client identity. */
public interface RateLimiter {

    /** Result of a limiter check; {@code retryAfter} is only meaningful when not allowed. */
    record Decision(boolean allowed, long remaining, Duration retryAfter) {}

    Decision check(String clientKey);
}
