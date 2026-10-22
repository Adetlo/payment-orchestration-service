package io.github.adetlo.payments.api;

import io.github.adetlo.payments.api.dto.ReconciliationRunResponse;
import io.github.adetlo.payments.api.dto.SystemHealthResponse;
import io.github.adetlo.payments.api.dto.SystemStatsResponse;
import io.github.adetlo.payments.application.SystemStatsService;
import io.github.adetlo.payments.infrastructure.health.SystemHealthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dashboard endpoints: component health (always 200) and aggregate statistics. */
@RestController
@RequestMapping(path = "/api/v1/system", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "System")
public class SystemController {

    private final SystemHealthService healthService;
    private final SystemStatsService statsService;

    public SystemController(SystemHealthService healthService, SystemStatsService statsService) {
        this.healthService = healthService;
        this.statsService = statsService;
    }

    @Operation(summary = "Component health; always 200 so the UI can render DEGRADED/DOWN")
    @GetMapping("/health")
    public SystemHealthResponse health() {
        SystemHealthService.SystemHealth health = healthService.check();
        Map<String, SystemHealthResponse.ComponentHealth> components = new LinkedHashMap<>();
        health.components()
                .forEach(
                        (name, c) ->
                                components.put(
                                        name,
                                        new SystemHealthResponse.ComponentHealth(
                                                c.status(), c.detail())));
        return new SystemHealthResponse(health.status(), components);
    }

    @Operation(summary = "Aggregate counters for the dashboard")
    @GetMapping("/stats")
    public SystemStatsResponse stats() {
        SystemStatsService.Stats stats = statsService.stats();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        stats.byStatus().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> byStatus.put(e.getKey().name(), e.getValue()));
        return new SystemStatsResponse(
                new SystemStatsResponse.Payments(stats.totalPayments(), byStatus),
                new SystemStatsResponse.Outbox(
                        stats.outboxPending(), stats.outboxFailed(), stats.outboxPublished()),
                new SystemStatsResponse.Kafka(stats.outboxPublished(), stats.deadLetterTotal()),
                new SystemStatsResponse.Reconciliation(
                        stats.lastRun().map(ReconciliationRunResponse::from).orElse(null)));
    }
}
