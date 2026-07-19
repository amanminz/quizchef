package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Restores an archived question to PUBLISHED: the same aggregate (same id,
 * same content, same quiz references) becomes available for new quizzes
 * again. Never a copy — quizzes referencing the id keep working unchanged.
 * A repeated restore is rejected by the aggregate (409), which is what
 * makes double-submitted restore commands safe.
 */
@Service
public class RestoreQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RestoreQuestionApplicationService.class);

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;
    private final AuthorizationService authorizationService;

    public RestoreQuestionApplicationService(QuestionRepository questionRepository,
                                             TagRepository tagRepository,
                                             AuthorizationService authorizationService) {
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public QuestionView restore(CurrentUser currentUser, UUID questionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        QuestionOwnership.requireOwner(currentUser, question);

        question.restore();
        questionRepository.saveAndFlush(question);

        log.info("Question {} restored to PUBLISHED", question.getId());
        return QuestionView.of(question, tagRepository.findAllById(question.tagIds()));
    }
}
