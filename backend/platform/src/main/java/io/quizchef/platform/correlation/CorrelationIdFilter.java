package io.quizchef.platform.correlation;

import io.quizchef.common.correlation.CorrelationKeys;
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
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes request correlation for every inbound HTTP request, before
 * Spring Security or any application code runs.
 *
 * <p>A caller-supplied {@code X-Correlation-Id} is reused (so a client's
 * retried request stays traceable across attempts); otherwise one is
 * generated. A {@code requestId} is always freshly minted — unique to this
 * one attempt, even when the correlation id is reused. Both are written to
 * SLF4J's MDC, which is how they reach every log line for the rest of the
 * request: this codebase runs each request synchronously on one thread, so
 * no method signature anywhere has to carry a correlation parameter
 * (RFC-010). The correlation id is also echoed on the response so a client
 * can show it in a fatal-error dialog and match it back to server logs.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} places this ahead of Spring
 * Security's filter (registered at {@code SecurityProperties.DEFAULT_FILTER_ORDER
 * = -100}), so correlation covers authentication failures too, not just
 * authenticated requests.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        try {
            MDC.put(CorrelationKeys.CORRELATION_ID_MDC_KEY, correlationId);
            MDC.put(CorrelationKeys.REQUEST_ID_MDC_KEY, UUID.randomUUID().toString());
            response.setHeader(CorrelationKeys.CORRELATION_ID_HEADER, correlationId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String supplied = request.getHeader(CorrelationKeys.CORRELATION_ID_HEADER);
        return StringUtils.hasText(supplied) ? supplied : UUID.randomUUID().toString();
    }
}
