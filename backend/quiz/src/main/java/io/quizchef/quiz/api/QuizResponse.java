package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuizView;
import io.quizchef.quiz.domain.QuizState;
import io.quizchef.quiz.domain.QuizVisibility;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A quiz as the authoring API presents it: metadata, settings, lifecycle,
 * available localizations, and the ordered question ids. Questions are
 * separate aggregates and are never embedded.
 */
public record QuizResponse(
        UUID id,
        UUID ownerIdentityId,
        @Schema(example = "en") String defaultLanguage,
        QuizState state,
        QuizVisibility visibility,
        @Schema(description = "Send this back with PUT; stale versions are rejected with 409")
        long version,
        QuizSettingsDto settings,
        List<QuizLocalizationDto> localizations,
        @Schema(description = "Question ids in display order") List<UUID> questionIds,
        Instant createdAt,
        Instant updatedAt
) {

    static QuizResponse from(QuizView view) {
        return new QuizResponse(
                view.id(),
                view.ownerIdentityId(),
                view.defaultLanguage().value(),
                view.state(),
                view.visibility(),
                view.version(),
                QuizSettingsDto.from(view.settings()),
                view.localizations().stream().map(QuizLocalizationDto::from).toList(),
                view.questionIds(),
                view.createdAt(),
                view.updatedAt());
    }
}
