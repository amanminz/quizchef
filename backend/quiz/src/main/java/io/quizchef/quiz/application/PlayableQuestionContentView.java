package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.QuestionType;
import java.util.List;
import java.util.UUID;

/**
 * The read-only, participant-safe projection of one question's content:
 * what a player's device must render to take part — the prompt and the
 * options in every authored language — and deliberately nothing more.
 * Correctness never appears here (that is {@link PlayableQuizView}, read by
 * the execution engine to score), and neither do explanations, which are
 * reveal-time material. The companion of {@link PlayableQuizView}: that
 * view is the engine's language-neutral scoring boundary, this one is the
 * display boundary the session module serves to participants.
 */
public record PlayableQuestionContentView(
        UUID questionId,
        QuestionType questionType,
        String defaultLanguage,
        List<PlayableOptionView> options,
        List<PlayableLocalizationView> localizations
) {

    /** One selectable option: identity and position only, never correctness. */
    public record PlayableOptionView(UUID optionId, int displayOrder) {
    }

    /** The question's displayable text in one language. */
    public record PlayableLocalizationView(
            String languageCode,
            String prompt,
            List<PlayableOptionTextView> optionTexts
    ) {
    }

    public record PlayableOptionTextView(UUID optionId, String text) {
    }
}
