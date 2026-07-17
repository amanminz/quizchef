package io.quizchef.session.application;

import io.quizchef.quiz.application.PlayableQuestionContentView;
import io.quizchef.session.domain.SessionPhase;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * The question in play, as a participant's device may see it: the
 * participant-safe content (via the quiz module's display boundary), where
 * it sits in the quiz, the phase, and the server's clock facts. Correct
 * option ids are present only once the phase has revealed them — before
 * that the field is null, so correctness never crosses the wire early
 * (ADR-006), matching exactly what the answer.revealed event discloses.
 */
public record CurrentQuestionView(
        UUID sessionId,
        SessionPhase phase,
        int questionNumber,
        int totalQuestions,
        int durationSeconds,
        /** The server's close time; null unless the question is open. */
        Instant endsAt,
        /** Milliseconds still on the clock; 0 unless the question is open. */
        long remainingMillis,
        PlayableQuestionContentView content,
        /** Null until the phase is ANSWER_REVEALED or LEADERBOARD. */
        Set<UUID> correctOptionIds
) {
}
