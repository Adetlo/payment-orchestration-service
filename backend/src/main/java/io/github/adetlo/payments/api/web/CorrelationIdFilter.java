package io.github.adetlo.payments.api.web;

import io.github.adetlo.payments.observability.MdcKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns a request id, echoes or generates the correlation id, puts both into the MDC and on the
 * response headers before any handler runs, so error responses carry them too.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTRIBUTE = "io.github.adetlo.payments.correlationId";
    private static final int MAX_LENGTH = 64;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = UUID.randomUUID().toString();
        String correlationId = sanitize(request.getHeader(CORRELATION_ID_HEADER));
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        MDC.put(MdcKeys.CORRELATION_ID, correlationId);
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(REQUEST_ID_HEADER, requestId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MdcKeys.REQUEST_ID);
            MDC.remove(MdcKeys.CORRELATION_ID);
        }
    }

    private static String sanitize(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        String trimmed = header.trim();
        if (trimmed.length() > MAX_LENGTH || !trimmed.matches("[A-Za-z0-9._-]+")) {
            return null;
        }
        return trimmed;
    }
}
