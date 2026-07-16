package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SessionPinTest {

    @Test
    void shouldAcceptExactlySixDigits() {
        assertThat(SessionPin.of("012345").value()).isEqualTo("012345");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"12345", "1234567", "12a456", " 123456", "abcdef", "12 456"})
    void shouldRejectAnythingElse(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> SessionPin.of(value));
    }

    @Test
    void equalPinsAreValueEqual() {
        assertThat(SessionPin.of("424242")).isEqualTo(SessionPin.of("424242"));
    }
}
