package io.quizchef.identity.infrastructure.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the JWT configuration properties.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfiguration {
}
