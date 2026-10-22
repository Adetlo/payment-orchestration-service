package io.github.adetlo.payments.api.dto;

import io.github.adetlo.payments.domain.Payment;
import java.time.Instant;
import java.util.UUID;

/** Public representation of a payment. */
public record PaymentResponse(
        UUID paymentId,
        String status,
        String payerAccount,
        String payeeAccount,
        String amount,
        String currency,
        String reference,
        String failureReason,
        int retryCount,
        int maxRetries,
        Instant nextRetryAt,
        String idempotencyKey,
        String correlationId,
        String demoFailureMode,
        Instant createdAt,
        Instant updatedAt) {

    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.paymentId(),
                payment.status().name(),
                payment.instruction().payerAccount().value(),
                payment.instruction().payeeAccount().value(),
                payment.money().amount().toPlainString(),
                payment.money().currencyCode(),
                payment.instruction().reference(),
                payment.failureReason(),
                payment.retryCount(),
                payment.maxRetries(),
                payment.nextRetryAt(),
                payment.idempotencyKey(),
                payment.correlationId(),
                payment.demoFailureMode() == null ? null : payment.demoFailureMode().headerValue(),
                payment.createdAt(),
                payment.updatedAt());
    }
}
