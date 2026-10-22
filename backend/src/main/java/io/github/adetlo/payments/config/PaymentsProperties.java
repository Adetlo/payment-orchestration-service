package io.github.adetlo.payments.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** All {@code payments.*} settings (see {@code application.yml}). */
@ConfigurationProperties(prefix = "payments")
public record PaymentsProperties(
        FailureInjection failureInjection,
        Retry retry,
        Outbox outbox,
        RateLimit ratelimit,
        Idempotency idempotency,
        Cors cors,
        @DefaultValue("65536") long maxRequestBytes) {

    /** {@code payments.failure-injection.*}: whether {@code X-Demo-Failure} is honoured. */
    public record FailureInjection(@DefaultValue("false") boolean enabled) {}

    /** {@code payments.retry.*}: settlement retry policy and scheduler cadence. */
    public record Retry(
            @DefaultValue("3") int maxAttempts,
            @DefaultValue("1s") Duration pollInterval,
            @DefaultValue("20") int batchSize) {}

    /** {@code payments.outbox.*}: outbox publisher cadence and send retry limit. */
    public record Outbox(
            @DefaultValue("500ms") Duration pollInterval,
            @DefaultValue("50") int batchSize,
            @DefaultValue("5") int maxAttempts) {}

    /** {@code payments.ratelimit.*}: fixed-window limit on {@code POST /api/v1/payments}. */
    public record RateLimit(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("200") int limit,
            @DefaultValue("1m") Duration window) {}

    /** {@code payments.idempotency.*}: record lifetime, Redis lock lifetime and cleanup cron. */
    public record Idempotency(
            @DefaultValue("24h") Duration ttl,
            @DefaultValue("10s") Duration lockTtl,
            @DefaultValue("0 0 * * * *") String cleanupCron) {}

    /** {@code payments.cors.*}: browser origins allowed to call the API. */
    public record Cors(
            @DefaultValue({"http://localhost:3000", "http://localhost:5173"})
                    List<String> allowedOrigins) {}
}
