package io.quizchef.quiz.application;

import io.quizchef.common.exception.ForbiddenException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizVisibility;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;

/**
 * The ownership policy of quiz authoring: only the owner modifies a quiz,
 * and private quizzes do not reveal their existence to anyone else.
 * Admin moderation of foreign quizzes is a future permission, not a
 * bypass here.
 */
final class QuizOwnership {

    private QuizOwnership() {
    }

    /**
     * Mutations are owner-only. Non-owners get 404 for private quizzes
     * (existence is not disclosed) and 403 for quizzes they can see.
     */
    static void requireOwner(CurrentUser currentUser, Quiz quiz) {
        if (isOwner(currentUser, quiz)) {
            return;
        }
        if (quiz.getVisibility() == QuizVisibility.PRIVATE) {
            throw new QuizNotFoundException(quiz.getId());
        }
        throw new ForbiddenException("quiz.ownership.required",
                "Only the quiz owner can modify this quiz");
    }

    /**
     * Reads: the owner always; everyone else only if the quiz is not
     * private (UNLISTED is reachable by id, PUBLIC additionally
     * discoverable once listing exists).
     */
    static void requireViewable(CurrentUser currentUser, Quiz quiz) {
        if (!isOwner(currentUser, quiz) && quiz.getVisibility() == QuizVisibility.PRIVATE) {
            throw new QuizNotFoundException(quiz.getId());
        }
    }

    private static boolean isOwner(CurrentUser currentUser, Quiz quiz) {
        return quiz.getOwnerIdentity().identityId().equals(currentUser.identityId());
    }
}
