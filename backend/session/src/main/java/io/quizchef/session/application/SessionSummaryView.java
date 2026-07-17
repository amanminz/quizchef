package io.quizchef.session.application;

import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.SessionState;
import java.time.Instant;
import java.util.UUID;

/**
 * The application layer's read model of a session: identity, host, lifecycle,
 * roster size, settings, and the execution pointers (current phase and
 * question). No scores or answers — those belong to the leaderboard and the
 * reconnection snapshot.
 */
public record SessionSummaryView(
        UUID sessionId,
        String sessionPin,
        SessionState state,
        SessionPhase currentPhase,
        UUID currentQuestionId,
        UUID hostIdentityId,
        UUID publishedQuizVersionId,
        int participantCount,
        SessionSettings settings,
        long version,
        Instant createdAt
) {

    public static SessionSummaryView of(Session session) {
        return new SessionSummaryView(
                session.getId(),
                session.getSessionPin().value(),
                session.getState(),
                session.getCurrentPhase(),
                session.getCurrentQuestionId(),
                session.getHostIdentity().identityId(),
                session.getPublishedQuizVersionId(),
                session.participantCount(),
                session.getSessionSettings(),
                session.getVersion(),
                session.getCreatedAt());
    }
}
