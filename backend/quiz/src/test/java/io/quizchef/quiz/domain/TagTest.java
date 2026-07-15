package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TagTest {

    @ParameterizedTest
    @CsvSource({
            "Moses, moses",
            "'  exodus  ', exodus",
            "OLD TESTAMENT, old testament",
            "Youth-Fellowship, youth-fellowship",
    })
    void shouldNormalizeNames(String raw, String expected) {
        assertThat(Tag.named(raw).getName()).isEqualTo(expected);
    }

    @Test
    void tagsWithTheSameNormalizedNameShareTheirName() {
        assertThat(Tag.named("Moses").getName()).isEqualTo(Tag.named(" moses ").getName());
    }

    @Test
    void tagsHaveTheirOwnIdentity() {
        Tag tag = Tag.named("exodus");

        assertThat(tag.getId()).isNotNull();
        assertThat(tag.getId()).isNotEqualTo(Tag.named("exodus").getId());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankNames(String name) {
        assertThatIllegalArgumentException().isThrownBy(() -> Tag.named(name));
    }

    @Test
    void shouldRejectOverlongNames() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Tag.named("x".repeat(51)));
    }
}
