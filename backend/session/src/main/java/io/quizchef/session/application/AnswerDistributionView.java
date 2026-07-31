package io.quizchef.session.application;

import java.util.List;
import java.util.UUID;

/**
 * How the current question's answers split across its options — the
 * authoritative counts behind the host's post-reveal "who picked what".
 * Counts only, never who; a participant's individual answer never appears
 * here (the same discipline as {@link AnswerProgressView}).
 *
 * <p>For a multiple-answer question, {@code options} counts option
 * <em>selections</em>, not participants — their sum may exceed
 * {@code answeredCount} since one participant can select several options.
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
