package io.quizchef.session.api;

import io.quizchef.session.application.ParticipantResultView;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * One participant's own progress during the quiz: what the question in
 * play awarded them, their running total, and the counts that frame it.
 *
 * <p><strong>Carries no rank</strong> — not a nulled field, no field at
 * all. Nobody is told where they stand until the host's ceremony has run;
 * a participant's own finish comes from
 * {@link ParticipantFinalPlacementResponse} after release, and no other
 * participant's name, score, or rank can reach a participant device from
 * here at any point.
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
        @Schema(description = "Their running total", example = "3420") int score,
        @Schema(description = "What the question in play awarded them; 0 if they did not answer",
                example = "750")
        int pointsEarned
) {

    static ParticipantResultResponse from(ParticipantResultView view) {
        return new ParticipantResultResponse(
                view.sessionId(),
                view.state(),
                view.currentPhase(),
                view.totalQuestions(),
                view.participantCount(),
                view.participantId(),
                view.displayName(),
                view.score(),
                view.pointsEarned());
    }
}
