package io.quizchef.common.configuration;

import com.fasterxml.jackson.core.StreamReadConstraints;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Caps the maximum length of a single JSON string Jackson will parse
 * (Phase 3 PR #3 / RFC-011) — belt-and-suspenders alongside {@code
 * platform}'s request-size filter, which already bounds the whole request
 * body well below Jackson's own default (20 MB per string).
 */
@Configuration
public class JacksonConfiguration {

    private static final int MAX_STRING_LENGTH = 65_536;

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer streamReadConstraintsCustomizer() {
        return builder -> builder.postConfigurer(objectMapper ->
                objectMapper.getFactory().setStreamReadConstraints(
                        StreamReadConstraints.builder().maxStringLength(MAX_STRING_LENGTH).build()));
    }
}
