package io.github.adetlo.payments.application;

/** Everything the API collected for a new payment, before domain validation. */
public record CreatePaymentCommand(
        String payerAccount,
        String payeeAccount,
        String amount,
        String currency,
        String reference,
        String idempotencyKey,
        String demoFailureHeader,
        String correlationId) {}
