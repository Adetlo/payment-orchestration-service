package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.application.FailureInjectionPolicy;
import io.github.adetlo.payments.application.port.SettlementOutcome;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.infrastructure.settlement.SimulatedSettlementGateway;
import io.github.adetlo.payments.support.PaymentFixtures;
import io.github.adetlo.payments.support.TestProperties;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SimulatedSettlementGatewayTest {

    private final SimulatedSettlementGateway enabled =
            new SimulatedSettlementGateway(
                    new FailureInjectionPolicy(TestProperties.withFailureInjection(true)));
    private final SimulatedSettlementGateway disabled =
            new SimulatedSettlementGateway(
                    new FailureInjectionPolicy(TestProperties.withFailureInjection(false)));

    private static Payment processing(DemoFailureMode mode) {
        Payment payment = PaymentFixtures.payment("10.00", "GBP", mode);
        payment.transitionTo(PaymentStatus.VALIDATED, Instant.now(), null);
        payment.transitionTo(PaymentStatus.PROCESSING, Instant.now(), null);
        return payment;
    }

    @Test
    void settlesOrdinaryPayments() {
        assertThat(enabled.settle(processing(null)).outcome()).isEqualTo(SettlementOutcome.SETTLED);
    }

    @Test
    void timeoutFailsFirstTwoAttemptsThenSettles() {
        Payment payment = processing(DemoFailureMode.SETTLEMENT_TIMEOUT);

        assertThat(enabled.settle(payment).outcome())
                .isEqualTo(SettlementOutcome.TRANSIENT_FAILURE);
        payment.transitionTo(PaymentStatus.RETRY_PENDING, Instant.now(), "t");
        payment.transitionTo(PaymentStatus.PROCESSING, Instant.now(), null);
        assertThat(enabled.settle(payment).outcome())
                .isEqualTo(SettlementOutcome.TRANSIENT_FAILURE);
        payment.transitionTo(PaymentStatus.RETRY_PENDING, Instant.now(), "t");
        payment.transitionTo(PaymentStatus.PROCESSING, Instant.now(), null);

        assertThat(payment.retryCount()).isEqualTo(2);
        assertThat(enabled.settle(payment).outcome()).isEqualTo(SettlementOutcome.SETTLED);
    }

    @Test
    void rejectedFailsPermanently() {
        var result = enabled.settle(processing(DemoFailureMode.SETTLEMENT_REJECTED));

        assertThat(result.outcome()).isEqualTo(SettlementOutcome.PERMANENT_FAILURE);
        assertThat(result.reason()).contains("rejected");
    }

    @Test
    void poisonModeDoesNotAffectSettlementItself() {
        assertThat(enabled.settle(processing(DemoFailureMode.POISON_MESSAGE)).isSettled()).isTrue();
    }

    @Test
    void modesAreIgnoredWhenInjectionIsDisabled() {
        assertThat(disabled.settle(processing(DemoFailureMode.SETTLEMENT_REJECTED)).isSettled())
                .isTrue();
        assertThat(disabled.settle(processing(DemoFailureMode.SETTLEMENT_TIMEOUT)).isSettled())
                .isTrue();
    }
}
