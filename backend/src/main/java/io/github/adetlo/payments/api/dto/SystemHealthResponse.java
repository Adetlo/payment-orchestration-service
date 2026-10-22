package io.github.adetlo.payments.api.dto;

import java.util.LinkedHashMap;
import java.util.Map;

/** Body of {@code GET /api/v1/system/health}. */
public record SystemHealthResponse(String status, Map<String, ComponentHealth> components) {

    public SystemHealthResponse {
        components = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(components));
    }

    /** Status and detail of one component. */
    public record ComponentHealth(String status, String detail) {}
}
