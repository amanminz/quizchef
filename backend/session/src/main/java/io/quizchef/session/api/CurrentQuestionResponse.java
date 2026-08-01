package io.quizchef.session.api;

import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.session.application.CurrentQuestionView;
import io.quizchef.session.domain.SessionPhase;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The question in play, as served to any device in the session — the
 * participant-safe rendering contract. Options carry no correctness;
 * {@code correctOptionIds} appears only once the phase has revealed it,
 * mirroring the answer.revealed event (ADR-006). Localizations carry every
 * authored language so a device renders the participant's preference and
 * falls back to the default.
 */
public record CurrentQuestionResponse(
        UUID sessionId,
        UUID questionId,
        SessionPhase phase,
        @Schema(description = "1-based position of this question in the quiz", example = "3")
        int questionNumber,
        int totalQuestions,
        QuestionType questionType,
        @Schema(example = "en") String defaultLanguage,
        int durationSeconds,
        @Schema(description = "The current phase's server clock: the reading-period end during "
                + "QUESTION_PREVIEW, the answer close time during QUESTION_OPEN; null otherwise")
        Instant endsAt,
        @Schema(description = "Milliseconds still on that clock; 0 when there is none")
        long remainingMillis,
        @Schema(description = "Empty during QUESTION_PREVIEW — options are reading-period material, "
                + "genuinely absent from the response, never merely hidden by the client")
        List<PlayableOptionDto> options,
        @Schema(description = "optionTexts is empty per localization during QUESTION_PREVIEW; prompt "
                + "is always present")
        List<PlayableLocalizationDto> localizations,
        @Schema(description = "Null until the answer has been revealed")
        Set<UUID> correctOptionIds
) {

    public record PlayableOptionDto(UUID optionId, int displayOrder) {
    }

    public record PlayableLocalizationDto(
            @Schema(example = "en") String languageCode,
            String prompt,
            @Schema(description = "The author's explanation; null until the answer is revealed")
            String explanation,
            List<PlayableOptionTextDto> optionTexts
    ) {
    }

    public record PlayableOptionTextDto(UUID optionId, String text) {
    }

    static CurrentQuestionResponse from(CurrentQuestionView view) {
        return new CurrentQuestionResponse(
                view.sessionId(),
                view.content().questionId(),
                view.phase(),
                view.questionNumber(),
                view.totalQuestions(),
                view.content().questionType(),
                view.content().defaultLanguage(),
                view.durationSeconds(),
                view.endsAt(),
                view.remainingMillis(),
                view.content().options().stream()
                        .map(option -> new PlayableOptionDto(option.optionId(), option.displayOrder()))
                        .toList(),
                view.content().localizations().stream()
                        .map(localization -> new PlayableLocalizationDto(
                                localization.languageCode(),
                                localization.prompt(),
                                localization.explanation(),
                                localization.optionTexts().stream()
                                        .map(text -> new PlayableOptionTextDto(text.optionId(), text.text()))
                                        .toList()))
                        .toList(),
                view.correctOptionIds());
    }
}
