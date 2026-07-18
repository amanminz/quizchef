package io.quizchef.platform.security.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers {@link RateLimitingInterceptor} against every route.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitWebMvcConfiguration implements WebMvcConfigurer {

    private final RateLimitingInterceptor rateLimitingInterceptor;

    public RateLimitWebMvcConfiguration(RateLimitingInterceptor rateLimitingInterceptor) {
        this.rateLimitingInterceptor = rateLimitingInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitingInterceptor);
    }
}
