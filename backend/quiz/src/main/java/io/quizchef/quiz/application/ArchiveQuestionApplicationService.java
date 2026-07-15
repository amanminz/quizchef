package io.quizchef.quiz.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.event.QuestionArchivedEvent;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Archives a published question: unavailable for new quizzes, retained
 * forever, and existing published quizzes continue functioning.
 */
@Service
public class ArchiveQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ArchiveQuestionApplicationService.class);

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ArchiveQuestionApplicationService(QuestionRepository questionRepository,
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
    public QuestionView archive(CurrentUser currentUser, ArchiveQuestionCommand command) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Question question = questionRepository.findById(command.questionId())
                .orElseThrow(() -> new QuestionNotFoundException(command.questionId()));
        QuestionOwnership.requireOwner(currentUser, question);

        question.archive();
        questionRepository.saveAndFlush(question);

        eventPublisher.publish(new QuestionArchivedEvent(question.getId(), clock.instant()));
        log.info("Question {} archived", question.getId());
        return QuestionView.of(question, tagRepository.findAllById(question.tagIds()));
    }
}
