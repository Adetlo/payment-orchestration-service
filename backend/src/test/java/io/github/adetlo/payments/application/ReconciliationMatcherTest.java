package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.application.port.SettlementFeedRecord;
import io.github.adetlo.payments.application.reconciliation.ReconciliationMatcher;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.domain.ReconciliationClassification;
import io.github.adetlo.payments.domain.ReconciliationItem;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationMatcherTest {

    private static final UUID RUN = UUID.randomUUID();

    private static SettlementFeedRecord external(Payment payment, String amount, String status) {
        return new SettlementFeedRecord(
                "SETTLE-X", payment.paymentId(), new BigDecimal(amount), "GBP", status);
    }

    @Test
    void matchedWhenAmountAndStatusAgree() {
        Payment completed = PaymentFixtures.paymentIn(PaymentStatus.COMPLETED);
        Payment failed = PaymentFixtures.paymentIn(PaymentStatus.FAILED);

        List<ReconciliationItem> items =
                ReconciliationMatcher.match(
                        RUN,
                        List.of(completed, failed),
                        List.of(
                                external(completed, "125.50", "SETTLED"),
                                external(failed, "125.50", "FAILED")));

        assertThat(items)
                .extracting(ReconciliationItem::classification)
                .containsOnly(ReconciliationClassification.MATCHED);
        assertThat(items.get(0).externalReference()).isEqualTo("SETTLE-X");
        assertThat(items.get(0).internalStatus()).isEqualTo("COMPLETED");
        assertThat(items.get(0).externalStatus()).isEqualTo("SETTLED");
        assertThat(items.get(0).details()).isNull();
    }

    @Test
    void amountMismatchTakesPrecedence() {
        Payment completed = PaymentFixtures.paymentIn(PaymentStatus.COMPLETED);

        List<ReconciliationItem> items =
                ReconciliationMatcher.match(
                        RUN, List.of(completed), List.of(external(completed, "125.51", "FAILED")));

        assertThat(items)
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.classification())
                                    .isEqualTo(ReconciliationClassification.AMOUNT_MISMATCH);
                            assertThat(item.internalAmount()).isEqualByComparingTo("125.50");
                            assertThat(item.externalAmount()).isEqualByComparingTo("125.51");
                            assertThat(item.details()).contains("125.50").contains("125.51");
                        });
    }

    @Test
    void statusMismatchWhenFailedPaymentReportedSettled() {
        Payment failed = PaymentFixtures.paymentIn(PaymentStatus.FAILED);

        List<ReconciliationItem> items =
                ReconciliationMatcher.match(
                        RUN, List.of(failed), List.of(external(failed, "125.50", "SETTLED")));

        assertThat(items)
                .singleElement()
                .extracting(ReconciliationItem::classification)
                .isEqualTo(ReconciliationClassification.STATUS_MISMATCH);
    }

    @Test
    void missingExternalWhenFeedLacksThePayment() {
        Payment completed = PaymentFixtures.paymentIn(PaymentStatus.COMPLETED);

        List<ReconciliationItem> items =
                ReconciliationMatcher.match(RUN, List.of(completed), List.of());

        assertThat(items)
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.classification())
                                    .isEqualTo(ReconciliationClassification.MISSING_EXTERNAL);
                            assertThat(item.paymentId()).isEqualTo(completed.paymentId());
                            assertThat(item.externalAmount()).isNull();
                        });
    }

    @Test
    void missingInternalForUnknownOrDuplicateExternalRecords() {
        Payment completed = PaymentFixtures.paymentIn(PaymentStatus.COMPLETED);
        SettlementFeedRecord unknown =
                new SettlementFeedRecord(
                        "SETTLE-U", UUID.randomUUID(), new BigDecimal("1.00"), "GBP", "SETTLED");
        SettlementFeedRecord noId =
                new SettlementFeedRecord(
                        "SETTLE-N", null, new BigDecimal("2.00"), "GBP", "SETTLED");

        List<ReconciliationItem> items =
                ReconciliationMatcher.match(
                        RUN,
                        List.of(completed),
                        List.of(
                                external(completed, "125.50", "SETTLED"),
                                unknown,
                                noId,
                                external(completed, "125.50", "SETTLED")));

        assertThat(items).hasSize(4);
        assertThat(items.get(0).classification()).isEqualTo(ReconciliationClassification.MATCHED);
        assertThat(items.subList(1, 4))
                .extracting(ReconciliationItem::classification)
                .containsOnly(ReconciliationClassification.MISSING_INTERNAL);
        assertThat(items)
                .extracting(ReconciliationItem::externalReference)
                .contains("SETTLE-U", "SETTLE-N");
    }
}
