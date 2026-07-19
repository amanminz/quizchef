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
        String quizTitle,
        int participantCount,
        SessionSettings settings,
        long version,
        Instant createdAt
) {

    public static SessionSummaryView of(Session session) {
        return of(session, null);
    }

    /**
     * With the quiz's participant-safe display title. Only the summary
     * read resolves it — command responses skip the extra quiz load and
     * carry null; clients learn the title from the read they render from.
     */
    public static SessionSummaryView of(Session session, String quizTitle) {
        return new SessionSummaryView(
                session.getId(),
                session.getSessionPin().value(),
                session.getState(),
                session.getCurrentPhase(),
                session.getCurrentQuestionId(),
                session.getHostIdentity().identityId(),
                session.getPublishedQuizVersionId(),
                quizTitle,
                session.participantCount(),
                session.getSessionSettings(),
                session.getVersion(),
                session.getCreatedAt());
    }
}
