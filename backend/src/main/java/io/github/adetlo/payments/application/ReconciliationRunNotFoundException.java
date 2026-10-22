package io.github.adetlo.payments.application;

import java.util.UUID;

/** Raised when a reconciliation run id is unknown; mapped to HTTP 404. */
public class ReconciliationRunNotFoundException extends RuntimeException {

    public ReconciliationRunNotFoundException(UUID runId) {
        super("Reconciliation run " + runId + " not found");
    }
}
