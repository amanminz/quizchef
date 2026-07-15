package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the question library. Questions are their author's private
 * assets: other identities get 404, never a hint of existence. The view
 * carries no quiz references — questions do not know where they are used.
 */
@Service
public class QuestionQueryService {

    private final QuestionRepository questionRepository;
    private final TagRepository tagRepository;
    private final AuthorizationService authorizationService;

    public QuestionQueryService(QuestionRepository questionRepository,
                                TagRepository tagRepository,
                                AuthorizationService authorizationService) {
        this.questionRepository = questionRepository;
        this.tagRepository = tagRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public QuestionView question(CurrentUser currentUser, UUID questionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_VIEW);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        QuestionOwnership.requireOwner(currentUser, question);
        return QuestionView.of(question, tagRepository.findAllById(question.tagIds()));
    }
}
