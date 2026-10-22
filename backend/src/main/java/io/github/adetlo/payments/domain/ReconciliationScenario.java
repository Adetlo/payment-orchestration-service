package io.github.adetlo.payments.domain;

import java.util.Arrays;
import java.util.Optional;

/** Which synthetic settlement feed a reconciliation run compares against. */
public enum ReconciliationScenario {
    CLEAN("clean"),
    WITH_DISCREPANCIES("with-discrepancies");

    private final String wireName;

    ReconciliationScenario(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }

    public static Optional<ReconciliationScenario> fromWire(String value) {
        if (value == null) {
            return Optional.empty();
        }
        return Arrays.stream(values())
                .filter(s -> s.wireName.equalsIgnoreCase(value.trim()))
                .findFirst();
    }
}
