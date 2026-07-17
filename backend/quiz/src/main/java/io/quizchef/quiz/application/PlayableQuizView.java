package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.Difficulty;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The read-only, gameplay-facing projection of a quiz: its questions in
 * authored order with just what the execution engine needs — which options
 * are correct (to score), the full option set (to validate submissions),
 * the difficulty (to weight the score), and the per-question time limit (to
 * size the timer).
 *
 * <p>Language-neutral by design: gameplay operates on ids and correctness,
 * never on translated text (RFC-003). This is the boundary the session
 * engine reads the quiz through — never the quiz repository.
 */
public record PlayableQuizView(
        int questionTimeLimitSeconds,
        List<PlayableQuestion> questions
) {

    public record PlayableQuestion(
            UUID questionId,
            Difficulty difficulty,
            Set<UUID> correctOptionIds,
            Set<UUID> allOptionIds
    ) {
    }
}
