package io.quizchef.session.application;

import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionSettings;
import io.quizchef.session.domain.SessionState;
import java.time.Instant;
import java.util.UUID;

/**
 * The application layer's read model of a session: identity, host, lifecycle,
 * roster size, and settings — no gameplay state.
 */
public record SessionSummaryView(
        UUID sessionId,
        String sessionPin,
        SessionState state,
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
                session.getHostIdentity().identityId(),
                session.getPublishedQuizVersionId(),
                session.participantCount(),
                session.getSessionSettings(),
                session.getVersion(),
                session.getCreatedAt());
    }
}
