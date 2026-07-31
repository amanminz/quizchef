package io.quizchef.session.application;

import java.util.List;
import java.util.UUID;

/**
 * How the current question's answers split across its options — the
 * authoritative counts behind the host's post-reveal "who picked what".
 * Counts only, never who; a participant's individual answer never appears
 * here (the same discipline as {@link AnswerProgressView}).
 *
 * <p>{@code OptionCount.percentage} is a share of {@code answeredCount}
 * (accepted answers), never of {@code eligibleParticipantCount} — dividing
 * by eligible would understate every option just because some eligible
 * participants never answered. {@code noAnswerCount} is exactly
 * {@code eligibleParticipantCount - answeredCount}: eligible participants
 * who submitted no accepted answer.
 *
 * <p>For a single/true-false question this makes
 * {@code sum(options[].percentage) ≈ 100}. For a multiple-answer question,
 * {@code options} counts option <em>selections</em>, not participants —
 * one participant can select several options, so both the raw counts and
 * the percentages may legitimately sum past {@code answeredCount} / 100.
 */
public record AnswerDistributionView(
        UUID sessionId,
        UUID questionId,
        int answeredCount,
        int eligibleParticipantCount,
        int noAnswerCount,
        List<OptionCount> options
) {

    public record OptionCount(UUID optionId, int count, int percentage) {
    }
}
