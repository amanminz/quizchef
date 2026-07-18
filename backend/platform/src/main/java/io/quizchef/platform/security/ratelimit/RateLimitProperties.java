package io.quizchef.platform.security.ratelimit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Abuse-prone-operation rate limits (Phase 3 PR #3 / RFC-011). Every
 * externally reachable route falls under {@code defaultRule} unless it has
 * its own entry in {@code rules}, keyed by exactly {@code "METHOD
 * route-pattern"} (the same resolved Spring MVC pattern
 * {@code RequestContextMdcInterceptor} already reads) — so no endpoint is
 * ever completely unbounded.
 *
 * <p>{@code enabled} exists so the automated test suite can disable this
 * outright: integration tests share one cached Spring context (and this
 * bean's in-memory buckets) across many test methods and even across test
 * classes, so a tight bucket exhausted by one test would spuriously fail
 * an unrelated later one. The {@code test} profile turns it off; a
 * dedicated integration test re-enables it via a property override to
 * prove the real 429 path still works, isolated in its own context.
 */
@ConfigurationProperties(prefix = "quizchef.security.rate-limit")
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        RateLimitRule defaultRule,
        Map<String, RateLimitRule> rules
) {

    public RateLimitProperties {
        if (defaultRule == null) {
            throw new IllegalArgumentException("quizchef.security.rate-limit.default-rule must be configured");
        }
        rules = rules == null ? Map.of() : Map.copyOf(rules);
    }

    RateLimitRule ruleFor(String method, String routePattern) {
        return rules.getOrDefault(method + " " + routePattern, defaultRule);
    }
}
