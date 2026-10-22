package io.github.adetlo.payments.infrastructure.kafka;

import io.github.adetlo.payments.application.event.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** Topics created by {@code KafkaAdmin} on startup. */
@Configuration
public class KafkaTopicsConfig {

    @Bean
    public NewTopic paymentEventsTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS).partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic paymentEventsDltTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_EVENTS_DLT).partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic reconciliationEventsTopic() {
        return TopicBuilder.name(KafkaTopics.RECONCILIATION_EVENTS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
