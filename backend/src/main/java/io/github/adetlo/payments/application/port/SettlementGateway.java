package io.github.adetlo.payments.application.port;

import io.github.adetlo.payments.domain.Payment;

/** Port to the (simulated) settlement network. */
public interface SettlementGateway {

    /** Attempts to settle the payment; never throws for business outcomes. */
    SettlementResult settle(Payment payment);
}
