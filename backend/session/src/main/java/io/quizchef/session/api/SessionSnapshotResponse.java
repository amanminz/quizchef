package io.quizchef.session.api;

import io.quizchef.session.application.SessionSnapshotView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The resume snapshot — this API's realization of the RFC-005 replay
 * contract: everything a returning participant needs to carry on exactly
 * where they left off. In the lobby most gameplay fields are empty (no
 * question, timer, or score yet).
 *
 * <p>Never carries the resume token. The credential is returned once, at
 * join, and this response is read on every arrival — echoing it back would
 * put the secret into far more places than it needs to be.
 */
public record SessionSnapshotResponse(
        UUID sessionId,
        UUID participantId,
        @Schema(description = "The participant's name as the server holds it", example = "Aman")
        String displayName,
        @Schema(example = "en") String preferredLanguage,
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
                view.displayName(),
                view.preferredLanguage(),
                view.sessionState(),
                view.currentQuestionId(),
                view.currentPhase(),
                view.remainingMillis(),
                view.participantScore(),
                view.submittedOptionIds(),
                view.leaderboard().stream().map(LeaderboardEntryDto::from).toList());
    }
}
