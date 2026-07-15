package io.quizchef.quiz.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.event.QuestionPublishedEvent;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a draft question, freezing it for reuse by quizzes. The
 * localization invariants a publish requires — default language present,
 * every option localized in every stored language — hold by construction,
 * so the aggregate only needs to guard the lifecycle.
 */
@Service
public class PublishQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(PublishQuestionApplicationService.class);

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public PublishQuestionApplicationService(QuestionRepository questionRepository,
                                             TagRepository tagRepository,
                                             AuthorizationService authorizationService,
                                             DomainEventPublisher eventPublisher,
                                             Clock clock) {
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public QuestionView publish(CurrentUser currentUser, PublishQuestionCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Question question = questionRepository.findById(command.questionId())
                .orElseThrow(() -> new QuestionNotFoundException(command.questionId()));
        QuestionOwnership.requireOwner(currentUser, question);

        question.publish();
        questionRepository.saveAndFlush(question);

        eventPublisher.publish(new QuestionPublishedEvent(question.getId(), clock.instant()));
        log.info("Question {} published", question.getId());
        return QuestionView.of(question, tagRepository.findAllById(question.tagIds()));
    }
}
