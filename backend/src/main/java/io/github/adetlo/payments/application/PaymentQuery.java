package io.github.adetlo.payments.application;

import io.github.adetlo.payments.domain.PaymentStatus;
import java.time.Instant;

/** Filters of the payment list endpoint. */
public record PaymentQuery(PaymentStatus status, String currency, Instant from, Instant to) {}
