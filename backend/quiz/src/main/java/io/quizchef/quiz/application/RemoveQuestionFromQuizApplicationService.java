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
 * Detaches a question from a quiz's composition. The aggregate enforces the
 * rule that matters: a published quiz may gain questions but never lose
 * them (participants may already rely on the composition) — draft only.
 *
 * <p>No domain event: nothing reacts to a draft's composition shrinking
 * (the same reasoning RFC-003 gives for not having a
 * {@code QuestionCreatedEvent}).
 */
@Service
public class RemoveQuestionFromQuizApplicationService {

    private static final Logger log =
            LoggerFactory.getLogger(RemoveQuestionFromQuizApplicationService.class);

    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;

    public RemoveQuestionFromQuizApplicationService(QuizRepository quizRepository,
                                                     AuthorizationService authorizationService) {
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public QuizView remove(CurrentUser currentUser, RemoveQuestionFromQuizCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Quiz quiz = quizRepository.findById(command.quizId())
                .orElseThrow(() -> new QuizNotFoundException(command.quizId()));
        QuizOwnership.requireOwner(currentUser, quiz);

        quiz.removeQuestion(command.questionId());
        quizRepository.saveAndFlush(quiz);

        log.info("Question {} detached from quiz {}", command.questionId(), quiz.getId());
        return QuizView.of(quiz);
    }
}
