package io.github.adetlo.payments.domain;

/** Raised when a value violates a domain rule; mapped to an HTTP 400 validation problem. */
public class DomainValidationException extends RuntimeException {

    private final String field;

    public DomainValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    /** The logical field (request property) that failed validation. */
    public String field() {
        return field;
    }
}
