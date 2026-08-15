package io.quizchef.session.application;

import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableLocalizationView;
import io.quizchef.quiz.application.PlayableQuestionContentView.PlayableOptionView;
import io.quizchef.quiz.domain.QuestionType;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The session's questions as the host sees them: the sequence actually
 * being played, plus the ones they pulled out of it.
 *
 * <p>Host material throughout, and the only place in the session module
 * where an unrevealed answer key crosses the wire. That is unavoidable and
 * deliberate — a host cannot fix a wrong answer key without being shown the
 * one it currently has — so it is confined to a single host-authenticated
 * read, never broadcast, and never reachable from a participant device.
 */
public record SessionQuestionListView(
        UUID sessionId,
        /** How many questions this session will actually ask — removals already excluded. */
        int totalQuestions,
        List<SessionQuestionView> questions
) {

    public record SessionQuestionView(
            UUID questionId,
            /** Its place in the effective sequence, 1-based. Null once removed. */
            Integer questionNumber,
            SessionQuestionStatus status,
            /** Whether the host has already corrected this question in this session. */
            boolean corrected,
            QuestionType questionType,
            String defaultLanguage,
            Set<UUID> correctOptionIds,
            List<PlayableOptionView> options,
            List<PlayableLocalizationView> localizations
    ) {
    }
}
