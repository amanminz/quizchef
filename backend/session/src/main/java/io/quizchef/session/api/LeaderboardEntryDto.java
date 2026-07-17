package io.quizchef.session.api;

import io.quizchef.session.domain.LeaderboardEntry;
import java.util.UUID;

/**
 * One ranked row of a leaderboard on the wire.
 */
public record LeaderboardEntryDto(
        UUID participantId,
        String displayName,
        int score,
        int rank
) {

    static LeaderboardEntryDto from(LeaderboardEntry entry) {
        return new LeaderboardEntryDto(
                entry.participantId(), entry.displayName(), entry.score(), entry.rank());
    }
}
