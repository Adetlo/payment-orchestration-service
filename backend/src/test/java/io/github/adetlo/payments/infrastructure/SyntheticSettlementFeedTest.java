package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.application.port.SettlementFeedRecord;
import io.github.adetlo.payments.application.reconciliation.ReconciliationMatcher;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.domain.ReconciliationClassification;
import io.github.adetlo.payments.domain.ReconciliationItem;
import io.github.adetlo.payments.domain.ReconciliationScenario;
import io.github.adetlo.payments.infrastructure.settlement.SyntheticSettlementFeed;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SyntheticSettlementFeedTest {

    private final SyntheticSettlementFeed feed = new SyntheticSettlementFeed();

    private static List<Payment> terminalPayments() {
        return List.of(
                PaymentFixtures.paymentIn(PaymentStatus.COMPLETED, "10.00", "GBP"),
                PaymentFixtures.paymentIn(PaymentStatus.COMPLETED, "20.00", "GBP"),
                PaymentFixtures.paymentIn(PaymentStatus.COMPLETED, "30.00", "GBP"),
                PaymentFixtures.paymentIn(PaymentStatus.FAILED, "40.00", "GBP"));
    }

    @Test
    void cleanScenarioMirrorsEveryPayment() {
        List<Payment> payments = terminalPayments();

        List<SettlementFeedRecord> records =
                feed.fetch(payments, ReconciliationScenario.CLEAN, 42L);

        assertThat(records).hasSize(4);
        assertThat(records)
                .extracting(SettlementFeedRecord::paymentId)
                .containsExactlyElementsOf(payments.stream().map(Payment::paymentId).toList());
        assertThat(records.get(3).status()).isEqualTo("FAILED");
        assertThat(records.get(0).externalReference()).startsWith("SETTLE-");
        assertThat(ReconciliationMatcher.match(UUID.randomUUID(), payments, records))
                .extracting(ReconciliationItem::classification)
                .containsOnly(ReconciliationClassification.MATCHED);
    }

    @Test
    void discrepancyScenarioInjectsOneOfEachKind() {
        List<Payment> payments = terminalPayments();

        List<SettlementFeedRecord> records =
                feed.fetch(payments, ReconciliationScenario.WITH_DISCREPANCIES, 42L);
        var counts =
                ReconciliationMatcher.match(UUID.randomUUID(), payments, records).stream()
                        .collect(
                                Collectors.groupingBy(
                                        ReconciliationItem::classification, Collectors.counting()));

        assertThat(counts)
                .containsEntry(ReconciliationClassification.AMOUNT_MISMATCH, 1L)
                .containsEntry(ReconciliationClassification.STATUS_MISMATCH, 1L)
                .containsEntry(ReconciliationClassification.MISSING_EXTERNAL, 1L)
                .containsEntry(ReconciliationClassification.MISSING_INTERNAL, 1L)
                .containsEntry(ReconciliationClassification.MATCHED, 1L);
    }

    @Test
    void discrepanciesAreDeterministicForASeed() {
        List<Payment> payments = terminalPayments();

        List<SettlementFeedRecord> first =
                feed.fetch(payments, ReconciliationScenario.WITH_DISCREPANCIES, 7L);
        List<SettlementFeedRecord> second =
                feed.fetch(payments, ReconciliationScenario.WITH_DISCREPANCIES, 7L);

        assertThat(first).isEqualTo(second);
    }

    @Test
    void discrepanciesDegradeGracefullyWithFewPayments() {
        List<Payment> onlyFailed =
                List.of(PaymentFixtures.paymentIn(PaymentStatus.FAILED, "5", "JPY"));

        List<SettlementFeedRecord> records =
                feed.fetch(onlyFailed, ReconciliationScenario.WITH_DISCREPANCIES, 1L);
        var items = ReconciliationMatcher.match(UUID.randomUUID(), onlyFailed, records);

        assertThat(items)
                .extracting(ReconciliationItem::classification)
                .containsExactlyInAnyOrder(
                        ReconciliationClassification.STATUS_MISMATCH,
                        ReconciliationClassification.MISSING_INTERNAL);
        assertThat(feed.fetch(List.of(), ReconciliationScenario.WITH_DISCREPANCIES, 1L)).hasSize(1);
        assertThat(feed.fetch(List.of(), ReconciliationScenario.CLEAN, 1L)).isEmpty();
    }
}
