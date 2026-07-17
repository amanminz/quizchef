package io.quizchef.quiz.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.event.QuestionAddedToQuizEvent;
import io.quizchef.quiz.domain.exception.QuestionNotAttachableException;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attaches a question to a quiz's composition — the RFC-003 "attach API"
 * reserved since the question library shipped. Draft and published
 * questions are both attachable — an author composes a quiz while its
 * questions are still being refined, the same way the quiz itself stays
 * DRAFT while assembled (RFC-003's own publish-time check, {@link
 * PublishQuizApplicationService}, is what actually requires every attached
 * question to carry the quiz's default language by the time the quiz
 * publishes). Only an ARCHIVED question is rejected — retired content is
 * unavailable for new use (RFC-003's own flagged gap — "ARCHIVED is not yet
 * enforced at attachment" — closed here). The Quiz aggregate enforces
 * everything else: no duplicates, and the quiz itself must be modifiable
 * (not archived).
 */
@Service
public class AddQuestionToQuizApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AddQuestionToQuizApplicationService.class);

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public AddQuestionToQuizApplicationService(QuizRepository quizRepository,
                                               QuestionRepository questionRepository,
                                               AuthorizationService authorizationService,
                                               DomainEventPublisher eventPublisher,
                                               Clock clock) {
        this.quizRepository = quizRepository;
        this.questionRepository = questionRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public QuizView add(CurrentUser currentUser, AddQuestionToQuizCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Quiz quiz = quizRepository.findById(command.quizId())
                .orElseThrow(() -> new QuizNotFoundException(command.quizId()));
        QuizOwnership.requireOwner(currentUser, quiz);

        Question question = questionRepository.findById(command.questionId())
                .orElseThrow(() -> new QuestionNotFoundException(command.questionId()));
        QuestionOwnership.requireOwner(currentUser, question);
        requireAttachable(question);

        quiz.addQuestion(question.getId());
        quizRepository.saveAndFlush(quiz);

        eventPublisher.publish(new QuestionAddedToQuizEvent(
                quiz.getId(), question.getId(), clock.instant()));
        log.info("Question {} attached to quiz {}", question.getId(), quiz.getId());
        return QuizView.of(quiz);
    }

    private static void requireAttachable(Question question) {
        if (question.isArchived()) {
            throw new QuestionNotAttachableException(
                    "Question %s is archived and unavailable for new quizzes".formatted(question.getId()));
        }
    }
}
