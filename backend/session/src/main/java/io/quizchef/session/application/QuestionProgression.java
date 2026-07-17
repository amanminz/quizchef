package io.quizchef.session.application;

import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The engine's sole authority on question order (ADR-006): given the quiz's
 * authored sequence and the question in play, it names the next one. The
 * host drives the pace with start/advance; it never picks the question.
 */
final class QuestionProgression {

    private QuestionProgression() {
    }

    /**
     * The question to open next: the first when none has played yet, the one
     * after {@code currentQuestionId} otherwise, or empty when the quiz is
     * exhausted (the caller then finishes the session).
     */
    static Optional<PlayableQuestion> nextAfter(PlayableQuizView quiz, UUID currentQuestionId) {
        List<PlayableQuestion> questions = quiz.questions();
        if (questions.isEmpty()) {
            return Optional.empty();
        }
        if (currentQuestionId == null) {
            return Optional.of(questions.getFirst());
        }
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).questionId().equals(currentQuestionId)) {
                int next = index + 1;
                return next < questions.size() ? Optional.of(questions.get(next)) : Optional.empty();
            }
        }
        return Optional.empty();
    }
}
