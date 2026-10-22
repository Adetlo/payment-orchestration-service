package io.github.adetlo.payments.domain;

/** Outcome of comparing one internal payment with the external settlement feed. */
public enum ReconciliationClassification {
    MATCHED,
    AMOUNT_MISMATCH,
    MISSING_INTERNAL,
    MISSING_EXTERNAL,
    STATUS_MISMATCH
}
