package io.quizchef.session.api;

import io.quizchef.session.application.SessionSummaryView;
import io.quizchef.session.domain.SessionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A session as the orchestration API presents it: identity, host, lifecycle,
 * roster size, and settings — no gameplay state.
 */
public record SessionSummaryResponse(
        UUID sessionId,
        @Schema(example = "042317") String sessionPin,
        SessionState state,
        UUID hostIdentityId,
        UUID publishedQuizVersionId,
        int participantCount,
        SessionSettingsDto settings,
        long version,
        Instant createdAt
) {

    static SessionSummaryResponse from(SessionSummaryView view) {
        return new SessionSummaryResponse(
                view.sessionId(),
                view.sessionPin(),
                view.state(),
                view.hostIdentityId(),
                view.publishedQuizVersionId(),
                view.participantCount(),
                SessionSettingsDto.from(view.settings()),
                view.version(),
                view.createdAt());
    }
}
