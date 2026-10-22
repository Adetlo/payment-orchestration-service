package io.github.adetlo.payments.infrastructure.persistence;

import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import java.time.Instant;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/** Composable filters for the payment list endpoint. */
public final class PaymentSpecifications {

    private PaymentSpecifications() {}

    public static Specification<Payment> filter(
            PaymentStatus status, String currency, Instant from, Instant to) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (status != null) {
                predicate = cb.and(predicate, cb.equal(root.get("status"), status));
            }
            if (currency != null && !currency.isBlank()) {
                predicate =
                        cb.and(
                                predicate,
                                cb.equal(
                                        root.get("currency"),
                                        currency.trim().toUpperCase(Locale.ROOT)));
            }
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return predicate;
        };
    }
}
