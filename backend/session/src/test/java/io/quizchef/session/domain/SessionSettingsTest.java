package io.quizchef.session.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SessionSettingsTest {

    @Test
    void defaultsAreReconnectFriendly() {
        SessionSettings defaults = SessionSettings.defaults();

        assertThat(defaults.allowLateJoin()).isTrue();
        assertThat(defaults.allowReconnect()).isTrue();
        assertThat(defaults.showLiveLeaderboard()).isTrue();
        assertThat(defaults.maxParticipants()).isEqualTo(500);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 1001})
    void shouldRejectMaxParticipantsOutOfRange(int max) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new SessionSettings(true, true, true, max));
    }

    @Test
    void shouldAcceptBoundaryMaxParticipants() {
        assertThat(new SessionSettings(false, false, false, 1).maxParticipants()).isEqualTo(1);
        assertThat(new SessionSettings(false, false, false, 1000).maxParticipants()).isEqualTo(1000);
    }
}
