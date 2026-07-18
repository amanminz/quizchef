package io.quizchef.platform.security.hardening;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.common.api.ApiError;
import io.quizchef.platform.security.logging.SecurityEventLogger;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects any request whose declared {@code Content-Length} exceeds a
 * configured cap, before any body parsing happens (Phase 3 PR #3 /
 * RFC-011). Ordered just inside {@link
 * io.quizchef.platform.logging.RequestLoggingFilter} so a rejection is
 * still correlation-traceable and still gets the per-request summary line.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class MaxRequestSizeFilter extends OncePerRequestFilter {

    private final long maxRequestSizeBytes;
    private final SecurityEventLogger securityEventLogger;
    private final ObjectMapper objectMapper;

    public MaxRequestSizeFilter(
            @Value("${quizchef.security.max-request-size-bytes:262144}") long maxRequestSizeBytes,
            SecurityEventLogger securityEventLogger,
            ObjectMapper objectMapper) {
        this.maxRequestSizeBytes = maxRequestSizeBytes;
        this.securityEventLogger = securityEventLogger;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long contentLength = request.getContentLengthLong();
        if (contentLength > maxRequestSizeBytes) {
            securityEventLogger.oversizedRequest(contentLength, maxRequestSizeBytes);
            response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiError.of("request.too-large", "Request body exceeds the maximum allowed size."));
            return;
        }
        filterChain.doFilter(request, response);
    }
}
