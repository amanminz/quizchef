package io.quizchef.session.api;

import io.quizchef.session.application.SessionResultsView;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * The session's standings and framing counts — interim (between questions,
 * once the answer is revealed) and final (after FINISHED) share this one
 * shape, so clients render both with the same components. Entries are the
 * same rows the leaderboard.updated broadcast carries.
 *
 * <p>{@code entries} is complete and unabridged — this is the host's
 * administrative read, and the host running the event is entitled to all
 * of it. {@code exactRankRevealCount} is the separate question of how
 * much of it belongs on a projector the whole room can see: render that
 * many places and stop. It is the same cutoff each participant's own
 * device is split by, so the big screen and the phones cannot disagree
 * about where the line falls.
 */
public record SessionResultsResponse(
        UUID sessionId,
        SessionState state,
        @Schema(description = "The gameplay phase while IN_PROGRESS; null once FINISHED")
        SessionPhase currentPhase,
        int totalQuestions,
        int participantCount,
        @Schema(description = "How many places may show an exact rank publicly — render this many "
                + "and stop; the remainder is host-only administrative data", example = "10")
        int exactRankRevealCount,
        List<LeaderboardEntryDto> entries
) {

    static SessionResultsResponse from(SessionResultsView view) {
        return new SessionResultsResponse(
                view.sessionId(),
                view.state(),
                view.currentPhase(),
                view.totalQuestions(),
                view.participantCount(),
                view.exactRankRevealCount(),
                view.entries().stream().map(LeaderboardEntryDto::from).toList());
    }
}
