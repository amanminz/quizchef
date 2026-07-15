package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class LanguageCodeTest {

    @ParameterizedTest
    @ValueSource(strings = {"en", "kn", "hi", "ta", "te", "ml", "en-IN", "zh-Hant", "es-419"})
    void shouldAcceptCommonBcp47Tags(String tag) {
        assertThat(LanguageCode.of(tag).value()).isEqualTo(tag);
    }

    @ParameterizedTest
    @CsvSource({
            "EN, en",
            "kN, kn",
            "en-in, en-IN",
            "EN-IN, en-IN",
            "ZH-HANT, zh-Hant",
            "es-419, es-419",
            "' en ', en",
    })
    void shouldNormalizeToCanonicalCase(String raw, String expected) {
        assertThat(LanguageCode.of(raw).value()).isEqualTo(expected);
    }

    @Test
    void normalizedTagsAreValueEqual() {
        assertThat(LanguageCode.of("EN-in")).isEqualTo(LanguageCode.of("en-IN"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "e", "english", "en_IN", "en-", "en-INDIA", "123", "en-IN-x-priv"})
    void shouldRejectMalformedTags(String tag) {
        assertThatIllegalArgumentException().isThrownBy(() -> LanguageCode.of(tag));
    }
}
