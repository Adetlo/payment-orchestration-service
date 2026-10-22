package io.github.adetlo.payments.application;

import io.github.adetlo.payments.application.port.SettlementGateway;
import io.github.adetlo.payments.application.port.SettlementResult;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Calls the settlement gateway for a PROCESSING payment and persists the outcome. */
@Component
public class SettlementRunner {

    private static final Logger log = LoggerFactory.getLogger(SettlementRunner.class);

    private final PaymentProcessingService processingService;
    private final SettlementGateway gateway;

    public SettlementRunner(PaymentProcessingService processingService, SettlementGateway gateway) {
        this.processingService = processingService;
        this.gateway = gateway;
    }

    public Optional<Payment> settle(UUID paymentId, AuditActor actor) {
        Optional<Payment> processing = processingService.findForSettlement(paymentId);
        if (processing.isEmpty()) {
            return Optional.empty();
        }
        SettlementResult result;
        try {
            result = gateway.settle(processing.get());
        } catch (RuntimeException e) {
            log.warn("Settlement gateway error for payment {}: {}", paymentId, e.toString());
            result =
                    SettlementResult.transientFailure(
                            "gateway error: " + e.getClass().getSimpleName());
        }
        return processingService.applySettlementResult(paymentId, result, actor);
    }
}
