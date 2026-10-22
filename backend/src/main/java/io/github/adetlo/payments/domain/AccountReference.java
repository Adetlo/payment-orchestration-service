package io.github.adetlo.payments.domain;

import java.util.Objects;
import java.util.regex.Pattern;

/** A synthetic IBAN-like account identifier: two letters, two digits, 8..30 alphanumerics. */
public record AccountReference(String value) {

    public static final String PATTERN = "^[A-Z]{2}[0-9]{2}[A-Z0-9]{8,30}$";
    private static final Pattern COMPILED = Pattern.compile(PATTERN);

    public AccountReference {
        Objects.requireNonNull(value, "value");
        if (!COMPILED.matcher(value).matches()) {
            throw new DomainValidationException(
                    "account", "account reference must match " + PATTERN);
        }
    }

    /** Masked form safe for logs and events, e.g. {@code GB**...0001}. */
    public String masked() {
        return value.substring(0, 2) + "**..." + value.substring(value.length() - 4);
    }

    @Override
    public String toString() {
        return masked();
    }
}
