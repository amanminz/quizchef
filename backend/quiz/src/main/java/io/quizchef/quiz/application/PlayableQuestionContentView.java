package io.quizchef.quiz.application;

import io.quizchef.quiz.domain.QuestionType;
import java.util.List;
import java.util.UUID;

/**
 * The read-only, participant-safe projection of one question's content:
 * what a player's device must render to take part — the prompt, the
 * options, and the author's explanation in every authored language.
 * Correctness never appears here (that is {@link PlayableQuizView}, read by
 * the execution engine to score). The explanation is reveal-time material:
 * this view carries it because the quiz module owns content, but the
 * session layer decides *when* it may cross the wire and strips it until
 * the answer is revealed (CurrentQuestionQueryService). The companion of
 * {@link PlayableQuizView}: that view is the engine's language-neutral
 * scoring boundary, this one is the display boundary the session module
 * serves to participants.
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
            /** The author's explanation; null when not authored, or stripped pre-reveal. */
            String explanation,
            List<PlayableOptionTextView> optionTexts
    ) {

        /** The same localization with the explanation withheld (pre-reveal). */
        public PlayableLocalizationView withoutExplanation() {
            return explanation == null
                    ? this
                    : new PlayableLocalizationView(languageCode, prompt, null, optionTexts);
        }
    }

    public record PlayableOptionTextView(UUID optionId, String text) {
    }
}
