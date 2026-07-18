package io.quizchef.platform.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quizchef.common.correlation.CorrelationKeys;
import io.quizchef.platform.security.logging.SecurityEventLogger;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

class RateLimitingInterceptorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-07-18T10:00:00Z"), ZoneId.of("UTC"));
    private final ObjectMapper objectMapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();

    private RateLimitingInterceptor interceptorWith(RateLimitProperties properties) {
        return new RateLimitingInterceptor(properties, new ClientIpResolver(),
                new SecurityEventLogger(), objectMapper, clock);
    }

    private MockHttpServletRequest requestFor(String method, String routePattern, String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, routePattern);
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, routePattern);
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void allowsRequestsWithinTheDefaultRuleAndSetsHeaders() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(true,
                new RateLimitRule(2, Duration.ofMinutes(1)), Map.of());
        RateLimitingInterceptor interceptor = interceptorWith(properties);
        MockHttpServletRequest request = requestFor("GET", "/api/v1/quizzes/mine", "127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean allowed = interceptor.preHandle(request, response, new Object());

        assertThat(allowed).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("2");
        assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo("1");
        assertThat(response.getHeader("X-RateLimit-Reset")).isNotNull();
    }

    @Test
    void blocksOnceTheBucketIsExhaustedWithA429() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(true,
                new RateLimitRule(1, Duration.ofMinutes(1)), Map.of());
        RateLimitingInterceptor interceptor = interceptorWith(properties);

        interceptor.preHandle(requestFor("POST", "/api/v1/auth/login", "127.0.0.1"),
                new MockHttpServletResponse(), new Object());

        MockHttpServletResponse blockedResponse = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(requestFor("POST", "/api/v1/auth/login", "127.0.0.1"),
                blockedResponse, new Object());

        assertThat(allowed).isFalse();
        assertThat(blockedResponse.getStatus()).isEqualTo(429);
        assertThat(blockedResponse.getHeader("Retry-After")).isNotNull();
        assertThat(blockedResponse.getContentAsString()).contains("rate-limit.exceeded");
    }

    @Test
    void usesTheRuleMatchingTheExactMethodAndRoute() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(true,
                new RateLimitRule(60, Duration.ofMinutes(1)),
                Map.of("POST /api/v1/auth/login", new RateLimitRule(1, Duration.ofMinutes(1))));
        RateLimitingInterceptor interceptor = interceptorWith(properties);

        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(requestFor("POST", "/api/v1/auth/login", "127.0.0.1"), response, new Object());

        assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("1");
    }

    @Test
    void identityAwareBucketIsSharedAcrossDifferentIpsForTheSameIdentity() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(true,
                new RateLimitRule(1, Duration.ofMinutes(1)), Map.of());
        RateLimitingInterceptor interceptor = interceptorWith(properties);

        MDC.put(CorrelationKeys.IDENTITY_ID_MDC_KEY, "identity-abc");
        interceptor.preHandle(requestFor("GET", "/api/v1/users/me", "10.0.0.1"),
                new MockHttpServletResponse(), new Object());

        MockHttpServletResponse second = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(requestFor("GET", "/api/v1/users/me", "10.0.0.2"),
                second, new Object());

        assertThat(allowed).isFalse();
    }

    @Test
    void anonymousCallersFromDifferentIpsGetSeparateBuckets() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(true,
                new RateLimitRule(1, Duration.ofMinutes(1)), Map.of());
        RateLimitingInterceptor interceptor = interceptorWith(properties);

        interceptor.preHandle(requestFor("POST", "/api/v1/auth/login", "10.0.0.1"),
                new MockHttpServletResponse(), new Object());

        MockHttpServletResponse fromDifferentIp = new MockHttpServletResponse();
        boolean allowed = interceptor.preHandle(requestFor("POST", "/api/v1/auth/login", "10.0.0.2"),
                fromDifferentIp, new Object());

        assertThat(allowed).isTrue();
    }

    @Test
    void doesNothingWhenDisabled() throws Exception {
        RateLimitProperties properties = new RateLimitProperties(false,
                new RateLimitRule(1, Duration.ofMinutes(1)), Map.of());
        RateLimitingInterceptor interceptor = interceptorWith(properties);

        MockHttpServletResponse response = new MockHttpServletResponse();
        interceptor.preHandle(requestFor("POST", "/api/v1/auth/login", "127.0.0.1"), response, new Object());
        boolean stillAllowed = interceptor.preHandle(requestFor("POST", "/api/v1/auth/login", "127.0.0.1"),
                new MockHttpServletResponse(), new Object());

        assertThat(stillAllowed).isTrue();
        assertThat(response.getHeader("X-RateLimit-Limit")).isNull();
    }
}
