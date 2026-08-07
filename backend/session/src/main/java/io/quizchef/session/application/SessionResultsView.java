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
 *
 * <p>{@code entries} is the host's complete, unabridged standings — this
 * is the administrative read, and the host running the event is entitled
 * to all of it. {@code exactRankRevealCount} is the separate question of
 * how much of it belongs on a projector the whole room can see: the
 * ceremony renders that many places and stops. Same number the
 * participants' own devices are split by
 * ({@link ParticipantFinalPlacementView}), computed once by
 * {@link io.quizchef.session.domain.FinalPlacementPolicy}, so the big
 * screen and the phones cannot disagree about where the line falls.
 */
public record SessionResultsView(
        UUID sessionId,
        SessionState state,
        SessionPhase currentPhase,
        int totalQuestions,
        int participantCount,
        int exactRankRevealCount,
        List<LeaderboardEntry> entries
) {
}
