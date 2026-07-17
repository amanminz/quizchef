package io.quizchef.session.application;

import io.quizchef.session.domain.LeaderboardEntry;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything a reconnecting participant needs to resume exactly where they
 * left off — the session's realization of the RFC-005 replay contract
 * (ADR-003, PRD "Welcome back"). Assembled by {@link SessionSnapshotAssembler}
 * from the live aggregates; the session module owns this so the transport can
 * project it without the session ever depending on the websocket module
 * (ADR-004).
 *
 * <p>During gameplay it carries the current phase and question, the time
 * still on the clock, the participant's own submitted answer and score, and
 * the standings. In the lobby those gameplay fields are simply empty.
 */
public record SessionSnapshotView(
        UUID sessionId,
        UUID participantId,
        String sessionState,
        String currentPhase,
        UUID currentQuestionId,
        long remainingMillis,
        int participantScore,
        Set<UUID> submittedOptionIds,
        List<LeaderboardEntry> leaderboard
) {
}
