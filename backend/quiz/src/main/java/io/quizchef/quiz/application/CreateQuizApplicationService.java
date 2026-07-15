package io.quizchef.quiz.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import io.quizchef.quiz.domain.event.QuizCreatedEvent;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates a draft quiz for the authenticated caller. The owner is always
 * {@code CurrentUser.reference()} — never accepted from the client.
 */
@Service
public class CreateQuizApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CreateQuizApplicationService.class);

    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public CreateQuizApplicationService(QuizRepository quizRepository,
                                        AuthorizationService authorizationService,
                                        DomainEventPublisher eventPublisher,
                                        Clock clock) {
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public QuizView create(CurrentUser currentUser, CreateQuizCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_CREATE);

        LanguageCode defaultLanguage = LanguageCode.of(command.defaultLanguage());
        QuizLocalization defaultContent = command.localization().toLocalization();
        if (!defaultContent.languageCode().equals(defaultLanguage)) {
            throw new IllegalArgumentException(
                    "localization languageCode must match defaultLanguage");
        }

        Quiz quiz = Quiz.create(defaultContent, currentUser.reference());
        if (command.visibility() != null) {
            quiz.changeVisibility(command.visibility());
        }
        if (command.settings() != null) {
            quiz.updateSettings(command.settings().toSettings());
        }
        quizRepository.save(quiz);

        eventPublisher.publish(new QuizCreatedEvent(
                quiz.getId(), quiz.getOwnerIdentity(), clock.instant()));
        log.info("Quiz {} created by identity {}", quiz.getId(), currentUser.identityId());
        return QuizView.of(quiz);
    }
}
