package io.quizchef.session.domain;

import jakarta.persistence.Embeddable;

/**
 * How a session executes — never how the quiz was authored (those settings
 * live on the Quiz). New execution settings join as new components without
 * a redesign.
 */
@Embeddable
public record SessionSettings(
        boolean allowLateJoin,
        boolean allowReconnect,
        boolean showLiveLeaderboard,
        int maxParticipants
) {

    static final int MIN_PARTICIPANTS = 1;
    static final int MAX_PARTICIPANTS = 1000;

    public SessionSettings {
        if (maxParticipants < MIN_PARTICIPANTS || maxParticipants > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "maxParticipants must be between %d and %d".formatted(MIN_PARTICIPANTS, MAX_PARTICIPANTS));
        }
    }

    /**
     * Kahoot-style defaults: late join and reconnection allowed, live
     * leaderboard on, a generous participant cap.
     */
    public static SessionSettings defaults() {
        return new SessionSettings(true, true, true, 500);
    }
}
