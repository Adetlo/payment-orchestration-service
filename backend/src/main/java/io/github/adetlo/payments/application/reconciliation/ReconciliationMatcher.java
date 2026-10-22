package io.github.adetlo.payments.application.reconciliation;

import io.github.adetlo.payments.application.port.SettlementFeedRecord;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.domain.ReconciliationClassification;
import io.github.adetlo.payments.domain.ReconciliationItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pure matching logic: pairs internal payments with external records by payment id. */
public final class ReconciliationMatcher {

    private ReconciliationMatcher() {}

    /** The external status that corresponds to an internal terminal status. */
    public static String expectedExternalStatus(PaymentStatus status) {
        return status == PaymentStatus.COMPLETED
                ? SettlementFeedRecord.STATUS_SETTLED
                : SettlementFeedRecord.STATUS_FAILED;
    }

    public static List<ReconciliationItem> match(
            UUID runId, List<Payment> internal, List<SettlementFeedRecord> external) {
        Map<UUID, SettlementFeedRecord> externalById = new LinkedHashMap<>();
        List<SettlementFeedRecord> orphans = new ArrayList<>();
        for (SettlementFeedRecord record : external) {
            if (record.paymentId() == null
                    || externalById.putIfAbsent(record.paymentId(), record) != null) {
                orphans.add(record);
            }
        }
        List<ReconciliationItem> items = new ArrayList<>();
        for (Payment payment : internal) {
            SettlementFeedRecord record = externalById.remove(payment.paymentId());
            items.add(classify(runId, payment, record));
        }
        orphans.addAll(externalById.values());
        for (SettlementFeedRecord orphan : orphans) {
            items.add(
                    ReconciliationItem.of(
                            runId,
                            orphan.paymentId(),
                            orphan.externalReference(),
                            ReconciliationClassification.MISSING_INTERNAL,
                            null,
                            orphan.amount(),
                            null,
                            orphan.status(),
                            "external record has no internal payment"));
        }
        return items;
    }

    private static ReconciliationItem classify(
            UUID runId, Payment payment, SettlementFeedRecord record) {
        String internalStatus = payment.status().name();
        if (record == null) {
            return ReconciliationItem.of(
                    runId,
                    payment.paymentId(),
                    null,
                    ReconciliationClassification.MISSING_EXTERNAL,
                    payment.money().amount(),
                    null,
                    internalStatus,
                    null,
                    "payment absent from the settlement feed");
        }
        ReconciliationClassification classification;
        String details;
        if (record.amount().compareTo(payment.money().amount()) != 0) {
            classification = ReconciliationClassification.AMOUNT_MISMATCH;
            details =
                    "internal "
                            + payment.money().amount().toPlainString()
                            + " vs external "
                            + record.amount().toPlainString();
        } else if (!expectedExternalStatus(payment.status()).equals(record.status())) {
            classification = ReconciliationClassification.STATUS_MISMATCH;
            details = "internal " + internalStatus + " vs external " + record.status();
        } else {
            classification = ReconciliationClassification.MATCHED;
            details = null;
        }
        return ReconciliationItem.of(
                runId,
                payment.paymentId(),
                record.externalReference(),
                classification,
                payment.money().amount(),
                record.amount(),
                internalStatus,
                record.status(),
                details);
    }
}
