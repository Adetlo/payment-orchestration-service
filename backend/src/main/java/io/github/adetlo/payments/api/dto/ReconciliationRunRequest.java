package io.github.adetlo.payments.api.dto;

import jakarta.validation.constraints.Pattern;

/** Body of {@code POST /api/v1/reconciliation/run}. */
public record ReconciliationRunRequest(
        @Pattern(
                        regexp = "^(clean|with-discrepancies)$",
                        message = "must be 'clean' or 'with-discrepancies'")
                String scenario,
        Long seed) {}
