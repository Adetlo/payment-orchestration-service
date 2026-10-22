package io.github.adetlo.payments.application;

import io.github.adetlo.payments.config.PaymentsProperties;
import io.github.adetlo.payments.domain.DemoFailureMode;
import io.github.adetlo.payments.domain.Payment;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Decides whether a payment's demo failure mode is in force (only when injection is enabled). */
@Component
public class FailureInjectionPolicy {

    private final boolean enabled;

    public FailureInjectionPolicy(PaymentsProperties properties) {
        this.enabled = properties.failureInjection().enabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Optional<DemoFailureMode> activeMode(Payment payment) {
        return enabled ? Optional.ofNullable(payment.demoFailureMode()) : Optional.empty();
    }

    public boolean isActive(Payment payment, DemoFailureMode mode) {
        return activeMode(payment).filter(mode::equals).isPresent();
    }
}
