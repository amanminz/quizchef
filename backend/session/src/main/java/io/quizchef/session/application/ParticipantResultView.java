package io.quizchef.session.application;

import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import java.util.UUID;

/**
 * One participant's own result — their row of the standings plus the
 * counts that frame it. The participant-facing counterpart of
 * {@link SessionResultsView}: deliberately carries a single entry so no
 * other participant's name, score, or rank can ride along to a
 * participant device (live-event privacy).
 */
public record ParticipantResultView(
        UUID sessionId,
        SessionState state,
        SessionPhase currentPhase,
        int totalQuestions,
        int participantCount,
        LeaderboardEntry entry
) {
}
