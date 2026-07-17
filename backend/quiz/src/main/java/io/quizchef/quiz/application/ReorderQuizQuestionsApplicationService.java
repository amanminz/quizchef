package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repositions a quiz's questions. Draft only — like every other
 * authored-content change (RFC-009: the editor's drag-and-drop composes
 * before publish, never after). No domain event: pure ordering has no
 * subscriber.
 */
@Service
public class ReorderQuizQuestionsApplicationService {

    private static final Logger log =
            LoggerFactory.getLogger(ReorderQuizQuestionsApplicationService.class);

    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;

    public ReorderQuizQuestionsApplicationService(QuizRepository quizRepository,
                                                   AuthorizationService authorizationService) {
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public QuizView reorder(CurrentUser currentUser, ReorderQuizQuestionsCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Quiz quiz = quizRepository.findById(command.quizId())
                .orElseThrow(() -> new QuizNotFoundException(command.quizId()));
        QuizOwnership.requireOwner(currentUser, quiz);

        quiz.reorder(command.orderedQuestionIds());
        quizRepository.saveAndFlush(quiz);

        log.info("Quiz {} questions reordered", quiz.getId());
        return QuizView.of(quiz);
    }
}
