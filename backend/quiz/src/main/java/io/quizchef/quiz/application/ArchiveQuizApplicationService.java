package io.quizchef.quiz.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.event.QuizArchivedEvent;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archives a published quiz — the terminal state. Archived quizzes are
 * retained, never deleted: sessions may have been played against them and
 * that history must stay reconstructable.
 */
@Service
public class ArchiveQuizApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveQuizApplicationService.class);

    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ArchiveQuizApplicationService(QuizRepository quizRepository,
                                         AuthorizationService authorizationService,
                                         DomainEventPublisher eventPublisher,
                                         Clock clock) {
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public QuizView archive(CurrentUser currentUser, ArchiveQuizCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Quiz quiz = quizRepository.findById(command.quizId())
                .orElseThrow(() -> new QuizNotFoundException(command.quizId()));
        QuizOwnership.requireOwner(currentUser, quiz);

        quiz.archive();
        quizRepository.saveAndFlush(quiz);

        eventPublisher.publish(new QuizArchivedEvent(quiz.getId(), clock.instant()));
        log.info("Quiz {} archived", quiz.getId());
        return QuizView.of(quiz);
    }
}
