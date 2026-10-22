package io.github.adetlo.payments.application.port;

/** How the settlement network answered. */
public enum SettlementOutcome {
    SETTLED,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE
}
