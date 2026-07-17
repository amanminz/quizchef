package io.quizchef.session.api;

import io.quizchef.session.application.SessionSnapshotView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The reconnection snapshot — this API's realization of the RFC-005 replay
 * contract: everything a returning participant needs to resume. In the lobby
 * most fields are empty (no question, timer, or score yet); the shape is the
 * final one, richer once gameplay exists.
 */
public record SessionSnapshotResponse(
        UUID sessionId,
        UUID participantId,
        String sessionState,
        UUID currentQuestionId,
        String currentPhase,
        long remainingMillis,
        int participantScore,
        Set<UUID> submittedOptionIds,
        List<LeaderboardEntryDto> leaderboard
) {

    static SessionSnapshotResponse from(SessionSnapshotView view) {
        return new SessionSnapshotResponse(
                view.sessionId(),
                view.participantId(),
                view.sessionState(),
                view.currentQuestionId(),
                view.currentPhase(),
                view.remainingMillis(),
                view.participantScore(),
                view.submittedOptionIds(),
                view.leaderboard().stream().map(LeaderboardEntryDto::from).toList());
    }
}
