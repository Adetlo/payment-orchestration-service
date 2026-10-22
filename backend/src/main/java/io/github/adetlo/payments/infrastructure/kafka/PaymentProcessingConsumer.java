package io.github.adetlo.payments.infrastructure.kafka;

import io.github.adetlo.payments.application.PaymentProcessingService;
import io.github.adetlo.payments.application.SettlementRunner;
import io.github.adetlo.payments.application.event.EventEnvelope;
import io.github.adetlo.payments.application.event.EventEnvelopeFactory;
import io.github.adetlo.payments.application.event.KafkaTopics;
import io.github.adetlo.payments.domain.AuditActor;
import io.github.adetlo.payments.domain.PaymentEventType;
import io.github.adetlo.payments.observability.MdcKeys;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code payment.events.v1}; reacts to {@code PaymentReceived} by validating and settling
 * the payment. Any exception propagates so the container's error handler can retry and dead-letter.
 */
@Component
public class PaymentProcessingConsumer {

    public static final String GROUP_ID = "payment-processor";
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingConsumer.class);

    private final EventEnvelopeFactory envelopeFactory;
    private final PaymentProcessingService processingService;
    private final SettlementRunner settlementRunner;

    public PaymentProcessingConsumer(
            EventEnvelopeFactory envelopeFactory,
            PaymentProcessingService processingService,
            SettlementRunner settlementRunner) {
        this.envelopeFactory = envelopeFactory;
        this.processingService = processingService;
        this.settlementRunner = settlementRunner;
    }

    @KafkaListener(topics = KafkaTopics.PAYMENT_EVENTS, groupId = GROUP_ID)
    public void onMessage(ConsumerRecord<String, String> record) {
        EventEnvelope envelope = envelopeFactory.parse(record.value());
        if (!PaymentEventType.PaymentReceived.name().equals(envelope.eventType())
                || envelope.paymentId() == null) {
            log.debug("Skipping {} for payment {}", envelope.eventType(), envelope.paymentId());
            return;
        }
        MDC.put(MdcKeys.PAYMENT_ID, envelope.paymentId().toString());
        MDC.put(MdcKeys.CORRELATION_ID, envelope.correlationId());
        try {
            if (processingService.onPaymentReceived(envelope.paymentId())) {
                settlementRunner.settle(envelope.paymentId(), AuditActor.CONSUMER);
            }
        } finally {
            MDC.remove(MdcKeys.PAYMENT_ID);
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }
}
