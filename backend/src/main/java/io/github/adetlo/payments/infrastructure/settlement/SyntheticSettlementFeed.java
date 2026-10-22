package io.github.adetlo.payments.infrastructure.settlement;

import io.github.adetlo.payments.application.port.SettlementFeed;
import io.github.adetlo.payments.application.port.SettlementFeedRecord;
import io.github.adetlo.payments.application.reconciliation.ReconciliationMatcher;
import io.github.adetlo.payments.domain.Money;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.domain.ReconciliationScenario;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Builds the external statement from the internal terminal payments. The {@code with-discrepancies}
 * scenario deterministically (seeded) injects one discrepancy of each kind, using a different
 * payment for each, as long as suitable payments exist.
 */
@Component
public class SyntheticSettlementFeed implements SettlementFeed {

    static final String REFERENCE_PREFIX = "SETTLE-";

    @Override
    public List<SettlementFeedRecord> fetch(
            List<Payment> internalPayments, ReconciliationScenario scenario, long seed) {
        List<SettlementFeedRecord> records = new ArrayList<>();
        for (Payment payment : internalPayments) {
            records.add(cleanRecord(payment));
        }
        if (scenario == ReconciliationScenario.WITH_DISCREPANCIES) {
            perturb(internalPayments, records, new Random(seed));
        }
        return records;
    }

    private static SettlementFeedRecord cleanRecord(Payment payment) {
        return new SettlementFeedRecord(
                externalReference(payment.paymentId()),
                payment.paymentId(),
                payment.money().amount(),
                payment.money().currencyCode(),
                ReconciliationMatcher.expectedExternalStatus(payment.status()));
    }

    static String externalReference(UUID paymentId) {
        return REFERENCE_PREFIX
                + paymentId.toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    private static void perturb(
            List<Payment> payments, List<SettlementFeedRecord> records, Random random) {
        List<Integer> completed = new ArrayList<>();
        List<Integer> failed = new ArrayList<>();
        for (int i = 0; i < payments.size(); i++) {
            (payments.get(i).status() == PaymentStatus.COMPLETED ? completed : failed).add(i);
        }
        // 1. AMOUNT_MISMATCH: a completed payment settled for one minor unit more.
        pick(completed, random)
                .ifPresent(
                        index -> {
                            Payment payment = payments.get(index);
                            Money bumped = payment.money().plus(payment.money().smallestUnit());
                            records.set(index, withAmount(records.get(index), bumped));
                        });
        // 2. STATUS_MISMATCH: a failed payment reported as settled.
        pick(failed, random)
                .ifPresent(
                        index ->
                                records.set(
                                        index,
                                        withStatus(
                                                records.get(index),
                                                SettlementFeedRecord.STATUS_SETTLED)));
        // 3. MISSING_EXTERNAL: a completed payment dropped from the statement (replaced by a
        // tombstone removed below).
        pick(completed, random).ifPresent(index -> records.set(index, null));
        records.removeIf(java.util.Objects::isNull);
        // 4. MISSING_INTERNAL: an external record we never saw.
        UUID unknown = new UUID(random.nextLong(), random.nextLong());
        String currency = payments.isEmpty() ? "GBP" : payments.get(0).money().currencyCode();
        records.add(
                new SettlementFeedRecord(
                        externalReference(unknown),
                        unknown,
                        Money.of(
                                        currency.equals("JPY") || currency.equals("IDR")
                                                ? "99"
                                                : "99.99",
                                        currency)
                                .amount(),
                        currency,
                        SettlementFeedRecord.STATUS_SETTLED));
    }

    /** Removes and returns a random index from the candidates (empty when none are left). */
    private static java.util.Optional<Integer> pick(List<Integer> candidates, Random random) {
        if (candidates.isEmpty()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(candidates.remove(random.nextInt(candidates.size())));
    }

    private static SettlementFeedRecord withAmount(SettlementFeedRecord record, Money amount) {
        return new SettlementFeedRecord(
                record.externalReference(),
                record.paymentId(),
                amount.amount(),
                record.currency(),
                record.status());
    }

    private static SettlementFeedRecord withStatus(SettlementFeedRecord record, String status) {
        return new SettlementFeedRecord(
                record.externalReference(),
                record.paymentId(),
                record.amount(),
                record.currency(),
                status);
    }
}
