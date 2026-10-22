package io.github.adetlo.payments.application;

import io.github.adetlo.payments.domain.AuditActor;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Who triggers a transition, why, and any extra detail to record with it. */
public record TransitionContext(AuditActor actor, String reason, Map<String, Object> extraDetails) {

    public TransitionContext {
        extraDetails =
                extraDetails == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(extraDetails));
    }

    public static TransitionContext of(AuditActor actor) {
        return new TransitionContext(actor, null, Map.of());
    }

    public static TransitionContext of(AuditActor actor, String reason) {
        return new TransitionContext(actor, reason, Map.of());
    }
}
