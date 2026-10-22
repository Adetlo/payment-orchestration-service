package io.github.adetlo.payments.application.port;

import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.ReconciliationScenario;
import java.util.List;

/** Port to the external settlement statement used by reconciliation. */
public interface SettlementFeed {

    /** Produces the statement that should correspond to the given internal payments. */
    List<SettlementFeedRecord> fetch(
            List<Payment> internalPayments, ReconciliationScenario scenario, long seed);
}
