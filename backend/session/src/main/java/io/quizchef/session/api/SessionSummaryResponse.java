package io.quizchef.session.api;

import io.quizchef.session.application.SessionSummaryView;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A session as the orchestration API presents it: identity, host, lifecycle,
 * roster size, settings, and the execution pointers (current phase and
 * question). No scores or answers.
 */
public record SessionSummaryResponse(
        UUID sessionId,
        @Schema(example = "042317") String sessionPin,
        SessionState state,
        @Schema(description = "The gameplay phase while IN_PROGRESS; null otherwise") SessionPhase currentPhase,
        UUID currentQuestionId,
        UUID hostIdentityId,
        UUID publishedQuizVersionId,
        @Schema(description = "The quiz's display title (default localization); present on the "
                + "summary read, null on command responses", example = "BELC Bible Quiz — Gospel of Mark")
        String quizTitle,
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
                view.currentPhase(),
                view.currentQuestionId(),
                view.hostIdentityId(),
                view.publishedQuizVersionId(),
                view.quizTitle(),
                view.participantCount(),
                SessionSettingsDto.from(view.settings()),
                view.version(),
                view.createdAt());
    }
}
