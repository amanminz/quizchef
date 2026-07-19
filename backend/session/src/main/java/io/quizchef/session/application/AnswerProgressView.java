package io.quizchef.session.application;

import java.util.UUID;

/**
 * How the current question's answering is going: how many participants
 * have an accepted answer, out of how many are eligible to answer right
 * now. Counts only — deliberately nothing about who has or has not
 * answered.
 */
public record AnswerProgressView(
        UUID sessionId,
        UUID questionId,
        int answeredCount,
        int eligibleCount
) {
}
