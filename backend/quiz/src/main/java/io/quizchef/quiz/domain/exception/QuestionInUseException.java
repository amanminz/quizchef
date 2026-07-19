package io.quizchef.quiz.domain.exception;

import io.quizchef.common.exception.ConflictException;

/**
 * A question referenced by at least one quiz was asked to be deleted.
 * Deletion is only possible for unused questions — quizzes rely on the
 * questions they compose, so the references must be removed first.
 */
public class QuestionInUseException extends ConflictException {

    private final long quizCount;

    public QuestionInUseException(long quizCount) {
        super("question.in-use",
                "This question is used in %d quiz%s. Remove it from those quizzes before deleting."
                        .formatted(quizCount, quizCount == 1 ? "" : "zes"));
        this.quizCount = quizCount;
    }

    public long quizCount() {
        return quizCount;
    }
}
