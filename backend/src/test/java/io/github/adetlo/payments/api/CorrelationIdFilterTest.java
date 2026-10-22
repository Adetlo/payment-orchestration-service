package io.github.adetlo.payments.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.adetlo.payments.api.web.CorrelationIdFilter;
import io.github.adetlo.payments.api.web.SecurityHeadersFilter;
import io.github.adetlo.payments.observability.MdcKeys;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void echoesProvidedCorrelationIdAndGeneratesRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/payments");
        request.addHeader("X-Correlation-Id", "corr-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringRequest = new AtomicReference<>();

        filter.doFilter(
                request,
                response,
                new MockFilterChain() {
                    @Override
                    public void doFilter(
                            jakarta.servlet.ServletRequest req,
                            jakarta.servlet.ServletResponse res) {
                        mdcDuringRequest.set(
                                MDC.get(MdcKeys.CORRELATION_ID)
                                        + "|"
                                        + MDC.get(MdcKeys.REQUEST_ID));
                    }
                });

        assertThat(response.getHeader("X-Correlation-Id")).isEqualTo("corr-123");
        assertThat(response.getHeader("X-Request-Id")).isNotBlank().isNotEqualTo("corr-123");
        assertThat(request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE))
                .isEqualTo("corr-123");
        assertThat(mdcDuringRequest.get()).startsWith("corr-123|").hasSizeGreaterThan(10);
        assertThat(MDC.get(MdcKeys.CORRELATION_ID)).isNull();
        assertThat(MDC.get(MdcKeys.REQUEST_ID)).isNull();
    }

    @Test
    void generatesCorrelationIdWhenMissingOrUnsafe() throws Exception {
        MockHttpServletRequest missing = new MockHttpServletRequest("GET", "/api/v1/payments");
        MockHttpServletResponse missingResponse = new MockHttpServletResponse();
        MockHttpServletRequest unsafe = new MockHttpServletRequest("GET", "/api/v1/payments");
        unsafe.addHeader("X-Correlation-Id", "<script>alert(1)</script>");
        MockHttpServletResponse unsafeResponse = new MockHttpServletResponse();

        filter.doFilter(missing, missingResponse, new MockFilterChain());
        filter.doFilter(unsafe, unsafeResponse, new MockFilterChain());

        assertThat(missingResponse.getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
        assertThat(unsafeResponse.getHeader("X-Correlation-Id")).matches("[0-9a-f-]{36}");
    }

    @Test
    void securityHeadersAreAddedAndApiResponsesAreNotCached() throws Exception {
        SecurityHeadersFilter securityHeaders = new SecurityHeadersFilter();
        MockHttpServletRequest api = new MockHttpServletRequest("GET", "/api/v1/payments");
        MockHttpServletResponse apiResponse = new MockHttpServletResponse();
        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/actuator/health");
        MockHttpServletResponse actuatorResponse = new MockHttpServletResponse();

        securityHeaders.doFilter(api, apiResponse, new MockFilterChain());
        securityHeaders.doFilter(actuator, actuatorResponse, new MockFilterChain());

        assertThat(apiResponse.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(apiResponse.getHeader("X-Frame-Options")).isEqualTo("DENY");
        assertThat(apiResponse.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(apiResponse.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(actuatorResponse.getHeader("Cache-Control")).isNull();
        assertThat(actuatorResponse.getHeader("X-Frame-Options")).isEqualTo("DENY");
    }
}
