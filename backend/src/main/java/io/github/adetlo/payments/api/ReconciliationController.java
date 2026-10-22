package io.github.adetlo.payments.api;

import io.github.adetlo.payments.api.dto.PageResponse;
import io.github.adetlo.payments.api.dto.ReconciliationRunRequest;
import io.github.adetlo.payments.api.dto.ReconciliationRunResponse;
import io.github.adetlo.payments.application.reconciliation.ReconciliationService;
import io.github.adetlo.payments.application.reconciliation.ReconciliationService.ReconciliationResult;
import io.github.adetlo.payments.domain.ReconciliationScenario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Reconciliation endpoints. */
@RestController
@RequestMapping(path = "/api/v1/reconciliation", produces = MediaType.APPLICATION_JSON_VALUE)
@Validated
@Tag(name = "Reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @Operation(summary = "Run reconciliation synchronously against the synthetic settlement feed")
    @PostMapping("/run")
    public ReconciliationRunResponse run(
            @Valid @RequestBody(required = false) ReconciliationRunRequest request) {
        ReconciliationScenario scenario =
                request == null
                        ? ReconciliationScenario.CLEAN
                        : ReconciliationScenario.fromWire(request.scenario())
                                .orElse(ReconciliationScenario.CLEAN);
        ReconciliationResult result =
                reconciliationService.run(scenario, request == null ? null : request.seed());
        return ReconciliationRunResponse.from(result.run(), result.items());
    }

    @Operation(summary = "List reconciliation runs, newest first")
    @GetMapping("/runs")
    public PageResponse<ReconciliationRunResponse> runs(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return PageResponse.of(
                reconciliationService.list(page, size), ReconciliationRunResponse::from);
    }

    @Operation(summary = "Get a reconciliation run with its items")
    @GetMapping("/runs/{runId}")
    public ReconciliationRunResponse get(@PathVariable UUID runId) {
        ReconciliationResult result = reconciliationService.get(runId);
        return ReconciliationRunResponse.from(result.run(), result.items());
    }
}
