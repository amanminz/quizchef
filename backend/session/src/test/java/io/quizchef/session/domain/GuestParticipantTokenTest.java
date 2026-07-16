package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GuestParticipantTokenTest {

    @Test
    void generatesUnpredictableUniqueTokens() {
        GuestParticipantToken first = GuestParticipantToken.generate();
        GuestParticipantToken second = GuestParticipantToken.generate();

        assertThat(first.value()).isNotBlank().hasSizeGreaterThan(20);
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void wrapsAndComparesByValue() {
        assertThat(GuestParticipantToken.of("abc")).isEqualTo(GuestParticipantToken.of("abc"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void shouldRejectBlankTokens(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> GuestParticipantToken.of(value));
    }
}
