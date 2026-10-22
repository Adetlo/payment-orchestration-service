package io.github.adetlo.payments.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReconciliationRunTest {

    @Test
    void completeDerivesCountsFromItems() {
        ReconciliationRun run =
                ReconciliationRun.start(
                        ReconciliationScenario.WITH_DISCREPANCIES, 42L, Instant.now());
        UUID runId = run.runId();
        List<ReconciliationItem> items =
                List.of(
                        item(runId, ReconciliationClassification.MATCHED),
                        item(runId, ReconciliationClassification.MATCHED),
                        item(runId, ReconciliationClassification.AMOUNT_MISMATCH),
                        item(runId, ReconciliationClassification.MISSING_INTERNAL),
                        item(runId, ReconciliationClassification.STATUS_MISMATCH));

        run.complete(items, Instant.now());

        assertThat(run.status()).isEqualTo(ReconciliationStatus.COMPLETED);
        assertThat(run.recordsChecked()).isEqualTo(5);
        assertThat(run.matched()).isEqualTo(2);
        assertThat(run.unmatched()).isEqualTo(3);
        assertThat(run.byClassification())
                .containsEntry(ReconciliationClassification.MATCHED, 2)
                .containsEntry(ReconciliationClassification.AMOUNT_MISMATCH, 1)
                .containsEntry(ReconciliationClassification.MISSING_INTERNAL, 1)
                .containsEntry(ReconciliationClassification.MISSING_EXTERNAL, 0)
                .containsEntry(ReconciliationClassification.STATUS_MISMATCH, 1);
        assertThat(run.completedAt()).isNotNull();
    }

    @Test
    void scenarioAndFailureModeParseFromWireNames() {
        assertThat(ReconciliationScenario.fromWire("with-discrepancies"))
                .contains(ReconciliationScenario.WITH_DISCREPANCIES);
        assertThat(ReconciliationScenario.fromWire("CLEAN")).contains(ReconciliationScenario.CLEAN);
        assertThat(ReconciliationScenario.fromWire("other")).isEmpty();
        assertThat(ReconciliationScenario.fromWire(null)).isEmpty();
        assertThat(DemoFailureMode.fromHeader("settlement-timeout"))
                .contains(DemoFailureMode.SETTLEMENT_TIMEOUT);
        assertThat(DemoFailureMode.fromHeader(" Poison-Message "))
                .contains(DemoFailureMode.POISON_MESSAGE);
        assertThat(DemoFailureMode.fromHeader("nope")).isEmpty();
        assertThat(AuditActor.SCHEDULER.wireName()).isEqualTo("scheduler");
        assertThat(PaymentEventType.forTarget(PaymentStatus.PROCESSING).isPublished()).isFalse();
        assertThat(PaymentEventType.forTarget(PaymentStatus.COMPLETED))
                .isEqualTo(PaymentEventType.PaymentCompleted);
    }

    @Test
    void failMarksRunFailed() {
        ReconciliationRun run =
                ReconciliationRun.start(ReconciliationScenario.CLEAN, 1L, Instant.now());

        run.fail(Instant.now());

        assertThat(run.status()).isEqualTo(ReconciliationStatus.FAILED);
        assertThat(run.completedAt()).isNotNull();
    }

    private static ReconciliationItem item(
            UUID runId, ReconciliationClassification classification) {
        return ReconciliationItem.of(
                runId,
                UUID.randomUUID(),
                "SETTLE-1",
                classification,
                BigDecimal.ONE,
                BigDecimal.ONE,
                "COMPLETED",
                "SETTLED",
                null);
    }
}
