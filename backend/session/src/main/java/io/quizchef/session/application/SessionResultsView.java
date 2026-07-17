package io.quizchef.session.application;

import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import java.util.List;
import java.util.UUID;

/**
 * The session's standings as a read model: the ranked entries (projected
 * fresh, never stored — ADR-006), plus the counts a results screen frames
 * them with. Serves both the between-questions leaderboard and the final
 * results after FINISHED — one shape, so the client renders interim and
 * final standings with the same components.
 */
public record SessionResultsView(
        UUID sessionId,
        SessionState state,
        SessionPhase currentPhase,
        int totalQuestions,
        int participantCount,
        List<LeaderboardEntry> entries
) {
}
