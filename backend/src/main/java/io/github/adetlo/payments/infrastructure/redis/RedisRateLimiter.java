package io.github.adetlo.payments.infrastructure.redis;

import io.github.adetlo.payments.application.port.RateLimiter;
import io.github.adetlo.payments.config.PaymentsProperties;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** Fixed-window counter per client key; skips limiting (with a warning) when Redis is down. */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    static final String PREFIX = "ratelimit:";

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final int limit;
    private final Duration window;

    public RedisRateLimiter(StringRedisTemplate redis, Clock clock, PaymentsProperties properties) {
        this.redis = redis;
        this.clock = clock;
        this.limit = properties.ratelimit().limit();
        this.window = properties.ratelimit().window();
    }

    @Override
    public Decision check(String clientKey) {
        long windowMillis = window.toMillis();
        long now = clock.millis();
        long windowStart = now - (now % windowMillis);
        String redisKey = PREFIX + clientKey + ":" + windowStart;
        try {
            Long count = redis.opsForValue().increment(redisKey);
            long current = count == null ? 1L : count;
            if (current == 1L) {
                redis.expire(redisKey, window);
            }
            long remaining = Math.max(0, limit - current);
            Duration retryAfter = Duration.ofMillis(windowStart + windowMillis - now);
            return new Decision(current <= limit, remaining, retryAfter);
        } catch (RuntimeException e) {
            log.warn(
                    "Redis unavailable for rate limiting ({}); request allowed",
                    e.getClass().getSimpleName());
            return new Decision(true, limit, Duration.ZERO);
        }
    }
}
