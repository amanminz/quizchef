package io.quizchef.session.application;

import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Session;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The engine's sole authority on question order (ADR-006): given the
 * sequence a session is playing and the question in play, it names the next
 * one. The host drives the pace with start/advance; it never picks the
 * question.
 *
 * <p>The sequence is the session's when it has one and the quiz's
 * otherwise. A session only has its own after the host shuffles it —
 * published quizzes are immutable, so a replay would otherwise repeat an
 * order the room already knows. Everything downstream (which question is
 * last, what number it is) reads through here, so the two cannot disagree.
 */
final class QuestionProgression {

    private QuestionProgression() {
    }

    /**
     * The question to open next: the first when none has played yet, the one
     * after {@code currentQuestionId} otherwise, or empty when the quiz is
     * exhausted (the caller then finishes the session).
     */
    static Optional<PlayableQuestion> nextAfter(PlayableQuizView quiz, Session session) {
        List<PlayableQuestion> questions = orderFor(quiz, session);
        UUID currentQuestionId = session.getCurrentQuestionId();
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

    /**
     * The sequence this session plays: its own if the host shuffled it, the
     * quiz's authored order otherwise.
     *
     * <p>Ignores any stored id the quiz no longer has and appends anything
     * the stored order is missing. Neither can happen while a published
     * quiz is immutable — but a stored order that silently disagreed with
     * the content would drop questions from a live game, which is a worse
     * failure than a slightly odd order, so it degrades rather than throws.
     */
    static List<PlayableQuestion> orderFor(PlayableQuizView quiz, Session session) {
        List<UUID> preferred = session.questionOrder();
        if (preferred.isEmpty()) {
            return quiz.questions();
        }
        Map<UUID, PlayableQuestion> byId = quiz.questions().stream()
                .collect(Collectors.toMap(PlayableQuestion::questionId, question -> question,
                        (first, second) -> first, LinkedHashMap::new));
        List<PlayableQuestion> ordered = new ArrayList<>(byId.size());
        preferred.stream().map(byId::remove).filter(Objects::nonNull).forEach(ordered::add);
        ordered.addAll(byId.values());
        return List.copyOf(ordered);
    }

    /** Where this question sits in the sequence the session is playing, 1-based. */
    static int numberOf(PlayableQuizView quiz, Session session, UUID questionId) {
        List<PlayableQuestion> questions = orderFor(quiz, session);
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).questionId().equals(questionId)) {
                return index + 1;
            }
        }
        return 0;
    }
}
