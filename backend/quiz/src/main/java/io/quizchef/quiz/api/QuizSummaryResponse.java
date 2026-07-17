package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuizSummaryView;
import io.quizchef.quiz.domain.QuizState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

/**
 * A quiz as "My Quizzes" presents it — a lean preview, not the full editable
 * representation ({@link QuizResponse}).
 */
public record QuizSummaryResponse(
        UUID id,
        String title,
        String description,
        QuizState state,
        @Schema(example = "en") String defaultLanguage,
        int questionCount,
        long version,
        Instant updatedAt
) {

    static QuizSummaryResponse from(QuizSummaryView view) {
        return new QuizSummaryResponse(
                view.id(),
                view.title(),
                view.description(),
                view.state(),
                view.defaultLanguage().value(),
                view.questionCount(),
                view.version(),
                view.updatedAt());
    }
}
