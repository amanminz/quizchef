package io.quizchef.session.api;

import io.quizchef.quiz.domain.QuestionType;
import io.quizchef.session.application.SessionQuestionListView;
import io.quizchef.session.application.SessionQuestionStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The host's own view of their session's questions: the sequence being
 * played, plus the ones they pulled out of it.
 *
 * <p>Unlike every other question read in this module, this one carries
 * {@code correctOptionIds} for questions that have not been revealed — a
 * host cannot fix a wrong answer key without seeing it. That is why the
 * endpoint is host-authenticated and why it is never broadcast.
 */
public record SessionQuestionsResponse(
        UUID sessionId,
        @Schema(description = "How many questions this session will actually ask — removals excluded",
                example = "4")
        int totalQuestions,
        List<SessionQuestionDto> questions
) {

    public record SessionQuestionDto(
            UUID questionId,
            @Schema(description = "Its place in the effective sequence, 1-based; null once removed",
                    example = "3")
            Integer questionNumber,
            SessionQuestionStatus status,
            @Schema(description = "Whether the host has already corrected this question in this session")
            boolean corrected,
            QuestionType questionType,
            @Schema(example = "en") String defaultLanguage,
            Set<UUID> correctOptionIds,
            List<OptionDto> options,
            List<LocalizationDto> localizations
    ) {
    }

    public record OptionDto(UUID optionId, int displayOrder) {
    }

    public record LocalizationDto(
            @Schema(example = "en") String languageCode,
            String prompt,
            String explanation,
            List<OptionTextDto> optionTexts
    ) {
    }

    public record OptionTextDto(UUID optionId, String text) {
    }

    static SessionQuestionsResponse from(SessionQuestionListView view) {
        return new SessionQuestionsResponse(
                view.sessionId(),
                view.totalQuestions(),
                view.questions().stream()
                        .map(question -> new SessionQuestionDto(
                                question.questionId(),
                                question.questionNumber(),
                                question.status(),
                                question.corrected(),
                                question.questionType(),
                                question.defaultLanguage(),
                                question.correctOptionIds(),
                                question.options().stream()
                                        .map(option -> new OptionDto(option.optionId(), option.displayOrder()))
                                        .toList(),
                                question.localizations().stream()
                                        .map(localization -> new LocalizationDto(
                                                localization.languageCode(),
                                                localization.prompt(),
                                                localization.explanation(),
                                                localization.optionTexts().stream()
                                                        .map(text -> new OptionTextDto(
                                                                text.optionId(), text.text()))
                                                        .toList()))
                                        .toList()))
                        .toList());
    }
}
