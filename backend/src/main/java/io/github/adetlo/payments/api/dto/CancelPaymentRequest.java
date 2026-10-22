package io.github.adetlo.payments.api.dto;

import jakarta.validation.constraints.Size;

/** Optional body of {@code POST /api/v1/payments/{id}/cancel}. */
public record CancelPaymentRequest(@Size(max = 256) String reason) {}
