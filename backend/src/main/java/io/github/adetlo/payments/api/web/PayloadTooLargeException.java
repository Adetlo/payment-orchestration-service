package io.github.adetlo.payments.api.web;

/** Mapped to HTTP 413. */
public class PayloadTooLargeException extends RuntimeException {

    public PayloadTooLargeException(long declared, long limit) {
        super("Request body of " + declared + " bytes exceeds the limit of " + limit + " bytes");
    }
}
