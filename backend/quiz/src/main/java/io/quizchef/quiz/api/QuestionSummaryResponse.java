package io.quizchef.quiz.api;

import io.quizchef.quiz.application.QuestionSummaryView;
import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.QuestionState;
import io.quizchef.quiz.domain.QuestionType;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A question as the library browse/picker presents it — a preview, not the
 * full editable representation ({@link QuestionResponse}).
 */
public record QuestionSummaryResponse(
        UUID id,
        String defaultLanguage,
        QuestionState state,
        QuestionType questionType,
        Difficulty difficulty,
        String title,
        Set<String> availableLanguages,
        List<QuestionResponse.TagDto> tags,
        long version,
        Instant updatedAt
) {

    static QuestionSummaryResponse from(QuestionSummaryView view) {
        return new QuestionSummaryResponse(
                view.id(),
                view.defaultLanguage().value(),
                view.state(),
                view.questionType(),
                view.difficulty(),
                view.title(),
                view.availableLanguages().stream().map(language -> language.value())
                        .collect(Collectors.toCollection(TreeSet::new)),
                view.tags().stream().map(tag -> new QuestionResponse.TagDto(tag.id(), tag.name())).toList(),
                view.version(),
                view.updatedAt());
    }
}
