package io.quizchef.common.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.exc.StreamConstraintsException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

class JacksonConfigurationTest {

    private final JacksonConfiguration configuration = new JacksonConfiguration();

    private ObjectMapper customizedObjectMapper() {
        Jackson2ObjectMapperBuilder builder = Jackson2ObjectMapperBuilder.json();
        configuration.streamReadConstraintsCustomizer().customize(builder);
        return builder.build();
    }

    @Test
    void capsTheMaximumJsonStringLength() {
        ObjectMapper objectMapper = customizedObjectMapper();

        assertThat(objectMapper.getFactory().streamReadConstraints().getMaxStringLength()).isEqualTo(65_536);
    }

    @Test
    void rejectsAStringBeyondTheCap() {
        ObjectMapper objectMapper = customizedObjectMapper();
        String oversizedJson = "\"" + "a".repeat(70_000) + "\"";

        assertThatThrownBy(() -> objectMapper.readValue(oversizedJson, String.class))
                .isInstanceOf(StreamConstraintsException.class);
    }

    @Test
    void acceptsAStringWithinTheCap() throws Exception {
        ObjectMapper objectMapper = customizedObjectMapper();
        String json = "\"" + "a".repeat(100) + "\"";

        assertThat(objectMapper.readValue(json, String.class)).hasSize(100);
    }
}
