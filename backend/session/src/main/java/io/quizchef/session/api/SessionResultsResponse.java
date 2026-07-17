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
 */
public record SessionResultsResponse(
        UUID sessionId,
        SessionState state,
        @Schema(description = "The gameplay phase while IN_PROGRESS; null once FINISHED")
        SessionPhase currentPhase,
        int totalQuestions,
        int participantCount,
        List<LeaderboardEntryDto> entries
) {

    static SessionResultsResponse from(SessionResultsView view) {
        return new SessionResultsResponse(
                view.sessionId(),
                view.state(),
                view.currentPhase(),
                view.totalQuestions(),
                view.participantCount(),
                view.entries().stream().map(LeaderboardEntryDto::from).toList());
    }
}
