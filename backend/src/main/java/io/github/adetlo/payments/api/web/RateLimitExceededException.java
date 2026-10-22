package io.github.adetlo.payments.api.web;

import java.time.Duration;

/** Mapped to HTTP 429 with a {@code Retry-After} header. */
public class RateLimitExceededException extends RuntimeException {

    private final Duration retryAfter;

    public RateLimitExceededException(Duration retryAfter) {
        super("Rate limit exceeded for POST /api/v1/payments");
        this.retryAfter = retryAfter;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
