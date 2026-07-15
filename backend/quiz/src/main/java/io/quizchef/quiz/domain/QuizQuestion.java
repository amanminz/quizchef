package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import java.util.Objects;
import java.util.UUID;

/**
 * One position in a quiz's composition: which question, at which place.
 *
 * <p>Lives inside the Quiz aggregate (the owning quiz id is the collection
 * key), because ordering invariants — unique question, unique position —
 * can only be enforced by the aggregate root.
 */
@Embeddable
public record QuizQuestion(
        UUID questionId,
        int displayOrder
) {

    public QuizQuestion {
        Objects.requireNonNull(questionId, "questionId must not be null");
        if (displayOrder < 1) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
    }
}
