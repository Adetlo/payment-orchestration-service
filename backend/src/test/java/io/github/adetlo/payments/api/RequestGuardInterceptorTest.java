package io.github.adetlo.payments.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.adetlo.payments.api.web.PayloadTooLargeException;
import io.github.adetlo.payments.api.web.RateLimitExceededException;
import io.github.adetlo.payments.api.web.RequestGuardInterceptor;
import io.github.adetlo.payments.application.port.RateLimiter;
import io.github.adetlo.payments.config.PaymentsProperties;
import io.github.adetlo.payments.support.TestProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestGuardInterceptorTest {

    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final RequestGuardInterceptor interceptor =
            new RequestGuardInterceptor(rateLimiter, TestProperties.defaults());

    private static MockHttpServletRequest post(String uri) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr("10.0.0.1");
        return request;
    }

    @Test
    void allowsRequestsUnderTheLimitAndExposesRemaining() {
        when(rateLimiter.check("10.0.0.1"))
                .thenReturn(new RateLimiter.Decision(true, 150, Duration.ofSeconds(30)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(interceptor.preHandle(post("/api/v1/payments"), response, new Object()))
                .isTrue();
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("150");
    }

    @Test
    void rejectsRequestsOverTheLimit() {
        when(rateLimiter.check("10.0.0.1"))
                .thenReturn(new RateLimiter.Decision(false, 0, Duration.ofSeconds(30)));
        MockHttpServletRequest request = post("/api/v1/payments");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(
                        e ->
                                assertThat(((RateLimitExceededException) e).retryAfter())
                                        .isEqualTo(Duration.ofSeconds(30)));
    }

    @Test
    void onlyPaymentCreationIsRateLimitedAndCanBeDisabled() {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/payments");
        get.setRequestURI("/api/v1/payments");
        MockHttpServletRequest cancel = post("/api/v1/payments/abc/cancel");
        PaymentsProperties disabled =
                new PaymentsProperties(
                        new PaymentsProperties.FailureInjection(false),
                        TestProperties.defaults().retry(),
                        TestProperties.defaults().outbox(),
                        new PaymentsProperties.RateLimit(false, 1, Duration.ofMinutes(1)),
                        TestProperties.defaults().idempotency(),
                        new PaymentsProperties.Cors(List.of()),
                        65536);

        assertThat(interceptor.preHandle(get, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(interceptor.preHandle(cancel, new MockHttpServletResponse(), new Object()))
                .isTrue();
        assertThat(
                        new RequestGuardInterceptor(rateLimiter, disabled)
                                .preHandle(
                                        post("/api/v1/payments"),
                                        new MockHttpServletResponse(),
                                        new Object()))
                .isTrue();
        verify(rateLimiter, never()).check(anyString());
    }

    @Test
    void rejectsBodiesLargerThanTheLimitBeforeReadingThem() {
        MockHttpServletRequest request = post("/api/v1/payments");
        request.setContent(new byte[70000]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("70000");
        verify(rateLimiter, never()).check(anyString());
    }

    @Test
    void clientKeyPrefersFirstForwardedAddress() {
        MockHttpServletRequest request = post("/api/v1/payments");
        request.addHeader("X-Forwarded-For", "203.0.113.9, 10.0.0.2");

        assertThat(RequestGuardInterceptor.clientKey(request)).isEqualTo("203.0.113.9");
        assertThat(RequestGuardInterceptor.clientKey(post("/x"))).isEqualTo("10.0.0.1");
    }
}
