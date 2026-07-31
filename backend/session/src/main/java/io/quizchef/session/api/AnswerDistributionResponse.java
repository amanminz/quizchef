package io.quizchef.session.api;

import io.quizchef.session.application.AnswerDistributionView;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

/**
 * How the current question's answers split across its options. {@code
 * percentage} is a share of {@code answeredCount} (accepted answers), never
 * of {@code eligibleParticipantCount} — for a single/true-false question
 * the option percentages sum to roughly 100. For a multiple-answer
 * question, {@code options} counts option <em>selections</em> — both the
 * counts and the percentages may legitimately sum past {@code
 * answeredCount} / 100, since one participant can select several options.
 * {@code noAnswerCount} is {@code eligibleParticipantCount - answeredCount}.
 */
public record AnswerDistributionResponse(
        UUID sessionId,
        UUID questionId,
        @Schema(example = "20") int answeredCount,
        @Schema(example = "20") int eligibleParticipantCount,
        @Schema(example = "0") int noAnswerCount,
        List<OptionCount> options
) {

    public record OptionCount(
            UUID optionId,
            @Schema(example = "12") int count,
            @Schema(description = "Percentage of answeredCount, never of eligibleParticipantCount",
                    example = "60") int percentage
    ) {
    }

    static AnswerDistributionResponse from(AnswerDistributionView view) {
        return new AnswerDistributionResponse(
                view.sessionId(),
                view.questionId(),
                view.answeredCount(),
                view.eligibleParticipantCount(),
                view.noAnswerCount(),
                view.options().stream()
                        .map(option -> new OptionCount(option.optionId(), option.count(), option.percentage()))
                        .toList());
    }
}
