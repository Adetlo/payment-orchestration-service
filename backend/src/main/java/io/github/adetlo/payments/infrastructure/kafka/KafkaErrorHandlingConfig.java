package io.github.adetlo.payments.infrastructure.kafka;

import io.github.adetlo.payments.application.event.KafkaTopics;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ConsumerRecordRecoverer;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/** Consumer retries (3 attempts, 500 ms apart) and dead-letter publishing. */
@Configuration
public class KafkaErrorHandlingConfig {

    public static final long BACKOFF_MS = 500L;
    public static final long MAX_RETRIES = 2L;

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, String> template, DeadLetterHandler deadLetterHandler) {
        DeadLetterPublishingRecoverer publisher =
                new DeadLetterPublishingRecoverer(
                        template,
                        (record, ex) ->
                                new TopicPartition(
                                        KafkaTopics.PAYMENT_EVENTS_DLT, record.partition()));
        ConsumerRecordRecoverer recoverer =
                (record, ex) -> {
                    publisher.accept(record, ex);
                    deadLetterHandler.onDeadLetter(record, ex);
                };
        DefaultErrorHandler handler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(BACKOFF_MS, MAX_RETRIES));
        handler.setCommitRecovered(true);
        return handler;
    }
}
