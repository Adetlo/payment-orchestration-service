package io.github.adetlo.payments.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adetlo.payments.application.port.RateLimiter;
import io.github.adetlo.payments.infrastructure.redis.RedisIdempotencyLock;
import io.github.adetlo.payments.infrastructure.redis.RedisRateLimiter;
import io.github.adetlo.payments.support.TestProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAdaptersTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void lockAcquiresWithNxAndTtlAndReleasesByDelete() {
        RedisIdempotencyLock lock = new RedisIdempotencyLock(redis, TestProperties.defaults());
        when(values.setIfAbsent(eq("idem:k1"), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true, false);

        assertThat(lock.tryAcquire("k1")).isTrue();
        assertThat(lock.tryAcquire("k1")).isFalse();
        lock.release("k1");

        verify(redis).delete("idem:k1");
    }

    @Test
    void lockFailsSafeWhenRedisIsDown() {
        RedisIdempotencyLock lock = new RedisIdempotencyLock(redis, TestProperties.defaults());
        when(values.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("down"));
        when(redis.delete(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(lock.tryAcquire("k1")).isTrue();
        lock.release("k1");
    }

    @Test
    void rateLimiterCountsWithinFixedWindow() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-22T10:00:30Z"), ZoneOffset.UTC);
        RedisRateLimiter limiter = new RedisRateLimiter(redis, clock, TestProperties.defaults());
        String key = "ratelimit:1.2.3.4:" + Instant.parse("2026-10-22T10:00:00Z").toEpochMilli();
        when(values.increment(key)).thenReturn(1L, 200L, 201L);

        RateLimiter.Decision first = limiter.check("1.2.3.4");
        RateLimiter.Decision last = limiter.check("1.2.3.4");
        RateLimiter.Decision over = limiter.check("1.2.3.4");

        assertThat(first.allowed()).isTrue();
        assertThat(first.remaining()).isEqualTo(199);
        assertThat(last.allowed()).isTrue();
        assertThat(last.remaining()).isZero();
        assertThat(over.allowed()).isFalse();
        assertThat(over.retryAfter()).isEqualTo(Duration.ofSeconds(30));
        verify(redis).expire(key, Duration.ofMinutes(1));
    }

    @Test
    void rateLimiterAllowsWhenRedisIsDown() {
        RedisRateLimiter limiter =
                new RedisRateLimiter(redis, Clock.systemUTC(), TestProperties.defaults());
        when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

        RateLimiter.Decision decision = limiter.check("1.2.3.4");

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.remaining()).isEqualTo(200);
    }
}
