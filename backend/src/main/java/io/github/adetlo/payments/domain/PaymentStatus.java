package io.github.adetlo.payments.domain;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Lifecycle states of a payment together with the explicit transition table. */
public enum PaymentStatus {
    RECEIVED,
    VALIDATED,
    PROCESSING,
    COMPLETED,
    REJECTED,
    FAILED,
    RETRY_PENDING,
    CANCELLED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> TRANSITIONS =
            new EnumMap<>(PaymentStatus.class);

    static {
        TRANSITIONS.put(RECEIVED, EnumSet.of(VALIDATED, REJECTED, CANCELLED));
        TRANSITIONS.put(VALIDATED, EnumSet.of(PROCESSING, CANCELLED));
        TRANSITIONS.put(PROCESSING, EnumSet.of(COMPLETED, RETRY_PENDING, FAILED));
        TRANSITIONS.put(RETRY_PENDING, EnumSet.of(PROCESSING, FAILED, CANCELLED));
        TRANSITIONS.put(COMPLETED, EnumSet.noneOf(PaymentStatus.class));
        TRANSITIONS.put(REJECTED, EnumSet.noneOf(PaymentStatus.class));
        TRANSITIONS.put(FAILED, EnumSet.noneOf(PaymentStatus.class));
        TRANSITIONS.put(CANCELLED, EnumSet.noneOf(PaymentStatus.class));
    }

    /** Statuses reachable from this one in a single transition. */
    public Set<PaymentStatus> allowedTransitions() {
        return EnumSet.copyOf(TRANSITIONS.get(this));
    }

    public boolean canTransitionTo(PaymentStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    public boolean isTerminal() {
        return TRANSITIONS.get(this).isEmpty();
    }

    /** Whether a client may cancel a payment in this status. */
    public boolean isCancellable() {
        return canTransitionTo(CANCELLED);
    }
}
