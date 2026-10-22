package io.github.adetlo.payments.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adetlo.payments.application.port.SettlementGateway;
import io.github.adetlo.payments.application.port.SettlementOutcome;
import io.github.adetlo.payments.application.port.SettlementResult;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.Payment;
import io.github.adetlo.payments.domain.PaymentStatus;
import io.github.adetlo.payments.support.PaymentFixtures;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SettlementRunnerTest {

    private final PaymentProcessingService processingService = mock(PaymentProcessingService.class);
    private final SettlementGateway gateway = mock(SettlementGateway.class);
    private final SettlementRunner runner = new SettlementRunner(processingService, gateway);

    @Test
    void passesGatewayResultToTheProcessingService() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        when(processingService.findForSettlement(payment.paymentId()))
                .thenReturn(Optional.of(payment));
        when(gateway.settle(payment)).thenReturn(SettlementResult.settled());
        when(processingService.applySettlementResult(any(), any(), any()))
                .thenReturn(Optional.of(payment));

        assertThat(runner.settle(payment.paymentId(), AuditActor.CONSUMER)).contains(payment);

        verify(processingService)
                .applySettlementResult(
                        eq(payment.paymentId()),
                        eq(SettlementResult.settled()),
                        eq(AuditActor.CONSUMER));
    }

    @Test
    void gatewayExceptionBecomesTransientFailure() {
        Payment payment = PaymentFixtures.paymentIn(PaymentStatus.PROCESSING);
        when(processingService.findForSettlement(payment.paymentId()))
                .thenReturn(Optional.of(payment));
        when(gateway.settle(payment)).thenThrow(new IllegalStateException("network"));

        runner.settle(payment.paymentId(), AuditActor.SCHEDULER);

        ArgumentCaptor<SettlementResult> result = ArgumentCaptor.forClass(SettlementResult.class);
        verify(processingService)
                .applySettlementResult(
                        eq(payment.paymentId()), result.capture(), eq(AuditActor.SCHEDULER));
        assertThat(result.getValue().outcome()).isEqualTo(SettlementOutcome.TRANSIENT_FAILURE);
        assertThat(result.getValue().reason()).contains("IllegalStateException");
    }

    @Test
    void skipsPaymentsThatAreNoLongerProcessing() {
        UUID paymentId = UUID.randomUUID();
        when(processingService.findForSettlement(paymentId)).thenReturn(Optional.empty());

        assertThat(runner.settle(paymentId, AuditActor.CONSUMER)).isEmpty();

        verify(gateway, never()).settle(any());
        verify(processingService, never()).applySettlementResult(any(), any(), any());
    }
}
