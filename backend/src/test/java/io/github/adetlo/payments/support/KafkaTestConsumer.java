package io.github.adetlo.payments.support;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * A throw-away Kafka consumer with its own group that reads a topic from the beginning and waits
 * for records matching a predicate.
 */
public final class KafkaTestConsumer implements AutoCloseable {

    private final KafkaConsumer<String, String> consumer;

    public KafkaTestConsumer(String bootstrapServers, String topic) {
        consumer =
                new KafkaConsumer<>(
                        Map.of(
                                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                                bootstrapServers,
                                ConsumerConfig.GROUP_ID_CONFIG,
                                "test-" + UUID.randomUUID(),
                                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                                "earliest",
                                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG,
                                "false",
                                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                                StringDeserializer.class.getName(),
                                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                                StringDeserializer.class.getName()));
        consumer.subscribe(List.of(topic));
    }

    /** Waits for the first record matching the predicate. */
    public ConsumerRecord<String, String> awaitRecord(
            Predicate<ConsumerRecord<String, String>> matcher, Duration timeout) {
        return awaitRecords(matcher, 1, timeout).get(0);
    }

    /** Waits until {@code count} matching records were seen. */
    public List<ConsumerRecord<String, String>> awaitRecords(
            Predicate<ConsumerRecord<String, String>> matcher, int count, Duration timeout) {
        Instant deadline = Instant.now().plus(timeout);
        List<ConsumerRecord<String, String>> matched = new ArrayList<>();
        while (Instant.now().isBefore(deadline)) {
            for (ConsumerRecord<String, String> record : consumer.poll(Duration.ofMillis(500))) {
                if (matcher.test(record)) {
                    matched.add(record);
                    if (matched.size() >= count) {
                        return matched;
                    }
                }
            }
        }
        throw new AssertionError(
                "Expected "
                        + count
                        + " matching Kafka record(s) within "
                        + timeout
                        + " but saw "
                        + matched.size());
    }

    @Override
    public void close() {
        consumer.close(Duration.ofSeconds(5));
    }
}
