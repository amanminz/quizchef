package io.quizchef.session.api;

import io.quizchef.session.application.ParticipantResultView;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One participant's own result — rank, score, display name, and the counts
 * that frame them. The participant-facing counterpart of
 * {@link SessionResultsResponse}: a single row by construction, so no other
 * participant's name, score, or rank can reach a participant device.
 */
public record ParticipantResultResponse(
        UUID sessionId,
        SessionState state,
        @Schema(description = "The gameplay phase while IN_PROGRESS; null once FINISHED")
        SessionPhase currentPhase,
        int totalQuestions,
        int participantCount,
        UUID participantId,
        String displayName,
        int rank,
        int score
) {

    static ParticipantResultResponse from(ParticipantResultView view) {
        return new ParticipantResultResponse(
                view.sessionId(),
                view.state(),
                view.currentPhase(),
                view.totalQuestions(),
                view.participantCount(),
                view.entry().participantId(),
                view.entry().displayName(),
                view.entry().rank(),
                view.entry().score());
    }
}
