package io.github.adetlo.payments.domain;

import java.util.Locale;

/** Who performed an audited action. */
public enum AuditActor {
    SYSTEM,
    API,
    SCHEDULER,
    CONSUMER;

    /** Lower-case wire representation ({@code system}, {@code api}, ...). */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
