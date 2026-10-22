package io.github.adetlo.payments.domain;

import java.util.Arrays;
import java.util.Optional;

/** Failure scenarios that can be injected through the {@code X-Demo-Failure} header. */
public enum DemoFailureMode {
    /** Transient failure: the first two settlement attempts time out, the third succeeds. */
    SETTLEMENT_TIMEOUT("settlement-timeout"),
    /** Permanent failure: settlement rejects the payment immediately. */
    SETTLEMENT_REJECTED("settlement-rejected"),
    /** The consumer throws on every delivery so the record is dead-lettered. */
    POISON_MESSAGE("poison-message");

    private final String headerValue;

    DemoFailureMode(String headerValue) {
        this.headerValue = headerValue;
    }

    /** The value used on the wire (header and API responses). */
    public String headerValue() {
        return headerValue;
    }

    public static Optional<DemoFailureMode> fromHeader(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(m -> m.headerValue.equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
