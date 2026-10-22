package io.github.adetlo.payments.support;

import io.github.adetlo.payments.config.PaymentsProperties;
import java.time.Duration;
import java.util.List;

/** Ready-made {@link PaymentsProperties} for unit tests. */
public final class TestProperties {

    private TestProperties() {}

    public static PaymentsProperties withFailureInjection(boolean enabled) {
        return new PaymentsProperties(
                new PaymentsProperties.FailureInjection(enabled),
                new PaymentsProperties.Retry(3, Duration.ofSeconds(1), 20),
                new PaymentsProperties.Outbox(Duration.ofMillis(500), 50, 5),
                new PaymentsProperties.RateLimit(true, 200, Duration.ofMinutes(1)),
                new PaymentsProperties.Idempotency(
                        Duration.ofHours(24), Duration.ofSeconds(10), "0 0 * * * *"),
                new PaymentsProperties.Cors(List.of("http://localhost:3000")),
                65536);
    }

    public static PaymentsProperties defaults() {
        return withFailureInjection(true);
    }
}
