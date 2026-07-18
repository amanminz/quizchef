package io.quizchef.platform.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Logs exactly one structured line per HTTP request, once it completes.
 *
 * <p>Runs just inside {@link io.quizchef.platform.correlation.CorrelationIdFilter}
 * — correlation and request ids are already in MDC by the time this fires,
 * so the line (and every line logged during the request) is searchable by
 * either. {@code operation} is the best-matching route pattern Spring MVC
 * resolved, not the raw path, so {@code /sessions/{id}/start} groups
 * consistently across different session ids.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMillis = System.currentTimeMillis() - startedAt;
            MDC.put("operation", operationOf(request));
            MDC.put("durationMs", String.valueOf(durationMillis));
            try {
                log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                        response.getStatus(), durationMillis);
            } finally {
                MDC.remove("operation");
                MDC.remove("durationMs");
            }
        }
    }

    private String operationOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        String route = pattern != null ? pattern.toString() : request.getRequestURI();
        return request.getMethod() + " " + route;
    }
}
