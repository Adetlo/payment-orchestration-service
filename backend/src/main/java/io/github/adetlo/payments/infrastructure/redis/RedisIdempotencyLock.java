package io.github.adetlo.payments.infrastructure.redis;

import io.github.adetlo.payments.application.port.IdempotencyLock;
import io.github.adetlo.payments.config.PaymentsProperties;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** {@code SET idem:{key} NX PX ttl}; fails safe (returns true) when Redis is unreachable. */
@Component
public class RedisIdempotencyLock implements IdempotencyLock {

    private static final Logger log = LoggerFactory.getLogger(RedisIdempotencyLock.class);
    static final String PREFIX = "idem:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisIdempotencyLock(StringRedisTemplate redis, PaymentsProperties properties) {
        this.redis = redis;
        this.ttl = properties.idempotency().lockTtl();
    }

    @Override
    public boolean tryAcquire(String key) {
        try {
            Boolean acquired = redis.opsForValue().setIfAbsent(PREFIX + key, "1", ttl);
            return !Boolean.FALSE.equals(acquired);
        } catch (RuntimeException e) {
            log.warn(
                    "Redis unavailable for idempotency lock ({}); relying on the database"
                            + " constraint",
                    e.getClass().getSimpleName());
            return true;
        }
    }

    @Override
    public void release(String key) {
        try {
            redis.delete(PREFIX + key);
        } catch (RuntimeException e) {
            log.debug("Could not release idempotency lock {}: {}", key, e.toString());
        }
    }
}
