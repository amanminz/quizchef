package io.quizchef.session.api;

import io.quizchef.session.domain.LeaderboardEntry;
import java.util.List;

/**
 * The current standings, highest first.
 */
public record LeaderboardResponse(List<LeaderboardEntryDto> entries) {

    static LeaderboardResponse from(List<LeaderboardEntry> entries) {
        return new LeaderboardResponse(entries.stream().map(LeaderboardEntryDto::from).toList());
    }
}
