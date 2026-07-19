package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.exception.QuestionInUseException;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a question that no quiz composes. One consistent rule across the
 * lifecycle: draft, published, or archived — a question is deletable if
 * and only if it is unused. The reference check and the delete run in the
 * same transaction, so a quiz attaching the question concurrently cannot
 * slip past the guard; the API rejects direct requests exactly like the
 * UI's disabled button (the button is a convenience, never the rule).
 */
@Service
public class DeleteQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(DeleteQuestionApplicationService.class);

    private final QuestionRepository questionRepository;
    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;

    public DeleteQuestionApplicationService(QuestionRepository questionRepository,
                                            QuizRepository quizRepository,
                                            AuthorizationService authorizationService) {
        this.questionRepository = questionRepository;
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional
    public void delete(CurrentUser currentUser, UUID questionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_EDIT);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        QuestionOwnership.requireOwner(currentUser, question);

        long quizCount = quizRepository.countByQuestionId(questionId);
        if (quizCount > 0) {
            throw new QuestionInUseException(quizCount);
        }

        questionRepository.delete(question);
        log.info("Question {} deleted", questionId);
    }

    /** How many quizzes compose the question — what the UI frames the delete affordance with. */
    @Transactional(readOnly = true)
    public long quizReferenceCount(CurrentUser currentUser, UUID questionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_VIEW);
        Question question = questionRepository.findById(questionId)
                .orElseThrow(() -> new QuestionNotFoundException(questionId));
        QuestionOwnership.requireOwner(currentUser, question);
        return quizRepository.countByQuestionId(questionId);
    }
}
