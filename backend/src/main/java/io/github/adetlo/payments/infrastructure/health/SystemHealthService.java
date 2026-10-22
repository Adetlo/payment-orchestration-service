package io.github.adetlo.payments.infrastructure.health;

import io.github.adetlo.payments.infrastructure.kafka.KafkaHealthIndicator;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

/**
 * Component-level health for {@code GET /api/v1/system/health}. Unlike the actuator endpoint this
 * always answers 200 and distinguishes DEGRADED (Kafka or Redis down: the API keeps accepting
 * payments) from DOWN (PostgreSQL unreachable).
 */
@Service
public class SystemHealthService {

    public static final String UP = "UP";
    public static final String DOWN = "DOWN";
    public static final String DEGRADED = "DEGRADED";

    private final DataSource dataSource;
    private final KafkaHealthIndicator kafkaHealth;
    private final RedisConnectionFactory redisConnectionFactory;

    public SystemHealthService(
            DataSource dataSource,
            KafkaHealthIndicator kafkaHealth,
            RedisConnectionFactory redisConnectionFactory) {
        this.dataSource = dataSource;
        this.kafkaHealth = kafkaHealth;
        this.redisConnectionFactory = redisConnectionFactory;
    }

    public SystemHealth check() {
        Map<String, Component> components = new LinkedHashMap<>();
        components.put("api", new Component(UP, "serving requests"));
        components.put("postgresql", postgres());
        components.put("kafka", kafka());
        components.put("redis", redis());
        String overall;
        if (!UP.equals(components.get("postgresql").status())) {
            overall = DOWN;
        } else if (components.values().stream().allMatch(c -> UP.equals(c.status()))) {
            overall = UP;
        } else {
            overall = DEGRADED;
        }
        return new SystemHealth(overall, components);
    }

    private Component postgres() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2)
                    ? new Component(UP, "connection valid")
                    : new Component(DOWN, "connection validation failed");
        } catch (Exception e) {
            return new Component(DOWN, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private Component kafka() {
        Health health = kafkaHealth.health();
        if (Status.UP.equals(health.getStatus())) {
            return new Component(
                    UP, "cluster reachable, nodes=" + health.getDetails().get("nodes"));
        }
        return new Component(DOWN, String.valueOf(health.getDetails().get("error")));
    }

    private Component redis() {
        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String pong = connection.ping();
            return "PONG".equalsIgnoreCase(pong)
                    ? new Component(UP, "ping ok")
                    : new Component(DOWN, "unexpected ping response: " + pong);
        } catch (Exception e) {
            return new Component(DOWN, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** One dependency's status and a short human-readable detail. */
    public record Component(String status, String detail) {}

    /** Overall status plus components. */
    public record SystemHealth(String status, Map<String, Component> components) {
        public SystemHealth {
            components = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(components));
        }
    }
}
