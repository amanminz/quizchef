package io.quizchef.platform.security.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quizchef.common.api.ApiError;
import io.quizchef.common.correlation.CorrelationKeys;
import io.quizchef.platform.security.logging.SecurityEventLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

/**
 * Identity-aware, IP-fallback rate limiting (Phase 3 PR #3 / RFC-011). A
 * {@link HandlerInterceptor}, not a {@link jakarta.servlet.Filter} — the
 * bucket is keyed by the resolved route <em>pattern</em>
 * ({@code /sessions/{id}/answers}, not the literal path), which — like
 * {@code RequestContextMdcInterceptor}'s path-variable extraction — is only
 * available once Spring MVC's handler mapping has run, i.e. inside
 * {@code preHandle}, the correct place to reject before the controller
 * executes.
 *
 * <p>Sets {@code X-RateLimit-Limit}/{@code -Remaining}/{@code -Reset} on
 * every response, allowed or blocked, plus {@code Retry-After} on the 429
 * case. Not consumed by the frontend today — added because they are
 * genuinely useful for future CLI clients, integrations, and debugging.
 */
@Component
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final RateLimitProperties properties;
    private final ClientIpResolver clientIpResolver;
    private final SecurityEventLogger securityEventLogger;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ConcurrentHashMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingInterceptor(RateLimitProperties properties, ClientIpResolver clientIpResolver,
                                   SecurityEventLogger securityEventLogger, ObjectMapper objectMapper,
                                   Clock clock) {
        this.properties = properties;
        this.clientIpResolver = clientIpResolver;
        this.securityEventLogger = securityEventLogger;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!properties.enabled()) {
            return true;
        }

        String method = request.getMethod();
        String route = routeOf(request);
        String subject = subjectOf(request);
        TokenBucket bucket = bucketFor(method, route, subject);

        boolean allowed = bucket.tryConsume();
        response.setHeader("X-RateLimit-Limit", String.valueOf(bucket.capacity()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, bucket.remaining())));
        response.setHeader("X-RateLimit-Reset", String.valueOf(bucket.secondsUntilReset()));

        if (allowed) {
            return true;
        }

        securityEventLogger.rateLimitTriggered(method, route, subject);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(bucket.secondsUntilReset()));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                ApiError.of("rate-limit.exceeded", "Too many requests. Please try again later."));
        return false;
    }

    private TokenBucket bucketFor(String method, String route, String subject) {
        String bucketKey = method + " " + route + "|" + subject;
        return buckets.computeIfAbsent(bucketKey, key -> {
            RateLimitRule rule = properties.ruleFor(method, route);
            return new TokenBucket(rule.capacity(), rule.window(), clock);
        });
    }

    private String routeOf(HttpServletRequest request) {
        Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        return pattern != null ? pattern.toString() : request.getRequestURI();
    }

    /** Identity-aware where authenticated; IP for anonymous callers. */
    private String subjectOf(HttpServletRequest request) {
        String identityId = MDC.get(CorrelationKeys.IDENTITY_ID_MDC_KEY);
        return identityId != null ? "identity:" + identityId : "ip:" + clientIpResolver.resolve(request);
    }
}
