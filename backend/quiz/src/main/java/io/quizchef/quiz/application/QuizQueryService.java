package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of quiz authoring. Owners see their quizzes in any state;
 * everyone else sees only non-private quizzes, and private quizzes of
 * other owners are indistinguishable from missing ones (404).
 */
@Service
public class QuizQueryService {

    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;

    public QuizQueryService(QuizRepository quizRepository,
                            AuthorizationService authorizationService) {
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public QuizView quiz(CurrentUser currentUser, UUID quizId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_VIEW);
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new QuizNotFoundException(quizId));
        QuizOwnership.requireViewable(currentUser, quiz);
        return QuizView.of(quiz);
    }
}
