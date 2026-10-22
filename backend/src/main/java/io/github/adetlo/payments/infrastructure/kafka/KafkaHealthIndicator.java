package io.github.adetlo.payments.infrastructure.kafka;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterOptions;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

/** Reports Kafka reachability via {@code describeCluster} with a 2 second budget. */
@Component("kafka")
public class KafkaHealthIndicator implements HealthIndicator, DisposableBean {

    public static final Duration TIMEOUT = Duration.ofSeconds(2);

    private final AdminClient adminClient;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.adminClient = AdminClient.create(Map.copyOf(kafkaAdmin.getConfigurationProperties()));
    }

    @Override
    public Health health() {
        try {
            DescribeClusterResult cluster =
                    adminClient.describeCluster(
                            new DescribeClusterOptions().timeoutMs((int) TIMEOUT.toMillis()));
            int nodes = cluster.nodes().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS).size();
            String clusterId = cluster.clusterId().get(TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            return Health.up()
                    .withDetail("nodes", nodes)
                    .withDetail("clusterId", clusterId)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("error", "interrupted").build();
        } catch (Exception e) {
            return Health.down()
                    .withDetail("error", e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    @Override
    public void destroy() {
        adminClient.close(Duration.ofSeconds(2));
    }
}
