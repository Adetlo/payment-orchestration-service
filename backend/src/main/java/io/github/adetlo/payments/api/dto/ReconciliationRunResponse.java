package io.github.adetlo.payments.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.adetlo.payments.domain.ReconciliationItem;
import io.github.adetlo.payments.domain.ReconciliationRun;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A reconciliation run; {@code items} is only present on the detail endpoint. */
public record ReconciliationRunResponse(
        UUID runId,
        String scenario,
        String status,
        Instant startedAt,
        Instant completedAt,
        int recordsChecked,
        int matched,
        int unmatched,
        Map<String, Integer> byClassification,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ReconciliationItemResponse> items) {

    public static ReconciliationRunResponse from(ReconciliationRun run) {
        return from(run, null);
    }

    public static ReconciliationRunResponse from(
            ReconciliationRun run, List<ReconciliationItem> items) {
        Map<String, Integer> byClassification = new LinkedHashMap<>();
        run.byClassification().forEach((k, v) -> byClassification.put(k.name(), v));
        return new ReconciliationRunResponse(
                run.runId(),
                run.scenario().wireName(),
                run.status().name(),
                run.startedAt(),
                run.completedAt(),
                run.recordsChecked(),
                run.matched(),
                run.unmatched(),
                byClassification,
                items == null
                        ? null
                        : items.stream().map(ReconciliationItemResponse::from).toList());
    }
}
