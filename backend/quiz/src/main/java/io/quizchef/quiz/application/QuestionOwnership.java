package io.quizchef.quiz.application;

import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;

/**
 * The ownership policy of the question library: questions are their
 * author's private assets for now — reads and mutations alike. There is no
 * visibility concept on questions yet, so other identities simply get 404
 * (existence is not disclosed). Shared and organization-wide vocabularies
 * arrive with the question bank (PRD v1.1).
 */
final class QuestionOwnership {

    private QuestionOwnership() {
    }

    static void requireOwner(CurrentUser currentUser, Question question) {
        if (!question.getOwnerIdentity().identityId().equals(currentUser.identityId())) {
            throw new QuestionNotFoundException(question.getId());
        }
    }
}
