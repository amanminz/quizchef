package io.quizchef.session.api;

import io.quizchef.session.domain.SessionSettings;

/**
 * A session's execution settings on the wire.
 */
public record SessionSettingsDto(
        boolean allowLateJoin,
        boolean allowReconnect,
        boolean showLiveLeaderboard,
        int maxParticipants
) {

    static SessionSettingsDto from(SessionSettings settings) {
        return new SessionSettingsDto(
                settings.allowLateJoin(),
                settings.allowReconnect(),
                settings.showLiveLeaderboard(),
                settings.maxParticipants());
    }
}
