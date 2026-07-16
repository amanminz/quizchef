package io.quizchef.session.application;

import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.Session;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a reconnecting participant needs to resume where they left off
 * — the session's realization of the RFC-005 replay contract. It lives in
 * the session module (which owns the state), so the transport can build its
 * wire snapshot from this without the session ever depending on the
 * websocket module (ADR-004).
 *
 * <p>Generation is deliberately simple for now: in the lobby there is no
 * question, no timer, and no score, so most fields are empty. The richer
 * gameplay values arrive with the scoring engine and {@code
 * SessionRecoveryService}; the shape is already final.
 *
 * @param leaderboard participant standings; empty until scoring exists
 */
public record SessionSnapshotView(
        UUID sessionId,
        UUID participantId,
        String sessionState,
        UUID currentQuestionId,
        String currentPhase,
        long remainingMillis,
        int participantScore,
        Set<UUID> submittedOptionIds,
        List<LeaderboardEntryView> leaderboard
) {

    public record LeaderboardEntryView(UUID participantId, String displayName, int score, int rank) {
    }

    /**
     * Assembles the snapshot from the live aggregates. No question or timer
     * is active before gameplay, so those are absent; the participant's cached
     * score is carried through.
     */
    public static SessionSnapshotView of(Session session, Participant participant) {
        return new SessionSnapshotView(
                session.getId(),
                participant.getId(),
                session.getState().name(),
                session.getCurrentQuestionId(),
                session.getCurrentPhase() == null ? null : session.getCurrentPhase().name(),
                0L,
                participant.getTotalScore(),
                Set.of(),
                List.of());
    }
}
