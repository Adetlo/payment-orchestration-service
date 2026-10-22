package io.github.adetlo.payments.api.web;

import io.github.adetlo.payments.application.port.RateLimiter;
import io.github.adetlo.payments.config.PaymentsProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Enforces the request body limit on every API call and the rate limit on payment creation. */
@Component
public class RequestGuardInterceptor implements HandlerInterceptor {

    static final String PAYMENTS_PATH = "/api/v1/payments";

    private final RateLimiter rateLimiter;
    private final boolean rateLimitEnabled;
    private final long maxRequestBytes;

    public RequestGuardInterceptor(RateLimiter rateLimiter, PaymentsProperties properties) {
        this.rateLimiter = rateLimiter;
        this.rateLimitEnabled = properties.ratelimit().enabled();
        this.maxRequestBytes = properties.maxRequestBytes();
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler) {
        long declared = request.getContentLengthLong();
        if (declared > maxRequestBytes) {
            throw new PayloadTooLargeException(declared, maxRequestBytes);
        }
        if (rateLimitEnabled
                && HttpMethod.POST.matches(request.getMethod())
                && PAYMENTS_PATH.equals(request.getRequestURI())) {
            RateLimiter.Decision decision = rateLimiter.check(clientKey(request));
            response.setHeader("X-RateLimit-Remaining", Long.toString(decision.remaining()));
            if (!decision.allowed()) {
                throw new RateLimitExceededException(decision.retryAfter());
            }
        }
        return true;
    }

    public static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
