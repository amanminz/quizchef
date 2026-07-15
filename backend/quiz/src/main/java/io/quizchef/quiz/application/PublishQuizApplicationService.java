package io.quizchef.quiz.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizQuestion;
import io.quizchef.quiz.domain.event.QuizPublishedEvent;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.domain.exception.QuizNotPublishableException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a draft quiz. The aggregate enforces its own lifecycle rules;
 * this service adds the one check that crosses aggregate boundaries —
 * every attached question must be localized in the quiz's default
 * language, or participants choosing the fallback would face untranslated
 * questions.
 */
@Service
public class PublishQuizApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PublishQuizApplicationService.class);

    private final QuizRepository quizRepository;
    private final QuestionRepository questionRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public PublishQuizApplicationService(QuizRepository quizRepository,
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
    public QuizView publish(CurrentUser currentUser, PublishQuizCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Quiz quiz = quizRepository.findById(command.quizId())
                .orElseThrow(() -> new QuizNotFoundException(command.quizId()));
        QuizOwnership.requireOwner(currentUser, quiz);

        quiz.publish();
        requireQuestionsLocalizedInDefaultLanguage(quiz);
        quizRepository.saveAndFlush(quiz);

        eventPublisher.publish(new QuizPublishedEvent(quiz.getId(), clock.instant()));
        log.info("Quiz {} published", quiz.getId());
        return QuizView.of(quiz);
    }

    private void requireQuestionsLocalizedInDefaultLanguage(Quiz quiz) {
        List<UUID> questionIds = quiz.questions().stream().map(QuizQuestion::questionId).toList();
        LanguageCode defaultLanguage = quiz.getDefaultLanguage();
        List<UUID> missing = questionRepository.findAllById(questionIds).stream()
                .filter(question -> question.localization(defaultLanguage).isEmpty())
                .map(Question::getId)
                .toList();
        if (!missing.isEmpty()) {
            throw new QuizNotPublishableException(
                    "Questions %s are not localized in the quiz's default language (%s)"
                            .formatted(missing, defaultLanguage.value()));
        }
    }
}
