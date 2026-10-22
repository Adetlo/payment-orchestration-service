package io.github.adetlo.payments.infrastructure.settlement;

import io.github.adetlo.payments.application.FailureInjectionPolicy;
import io.github.adetlo.payments.application.port.SettlementGateway;
import io.github.adetlo.payments.application.port.SettlementResult;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.Payment;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a settlement network. Settles everything unless failure injection is enabled and the
 * payment carries a demo failure mode: {@code settlement-timeout} fails the first two attempts,
 * {@code settlement-rejected} fails permanently. {@code poison-message} is handled by the consumer
 * before settlement is reached.
 */
@Component
public class SimulatedSettlementGateway implements SettlementGateway {

    /** Number of attempts that time out before a {@code settlement-timeout} payment settles. */
    public static final int TIMEOUT_ATTEMPTS = 2;

    private final FailureInjectionPolicy failureInjection;

    public SimulatedSettlementGateway(FailureInjectionPolicy failureInjection) {
        this.failureInjection = failureInjection;
    }

    @Override
    public SettlementResult settle(Payment payment) {
        DemoFailureMode mode = failureInjection.activeMode(payment).orElse(null);
        if (mode == DemoFailureMode.SETTLEMENT_REJECTED) {
            return SettlementResult.permanentFailure(
                    "settlement network rejected the payment (demo)");
        }
        if (mode == DemoFailureMode.SETTLEMENT_TIMEOUT && payment.retryCount() < TIMEOUT_ATTEMPTS) {
            return SettlementResult.transientFailure(
                    "settlement network timed out (demo, attempt "
                            + (payment.retryCount() + 1)
                            + ")");
        }
        return SettlementResult.settled();
    }
}
