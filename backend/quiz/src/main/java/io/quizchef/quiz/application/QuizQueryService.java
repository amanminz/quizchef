package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.exception.QuizNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuizRepository;
import io.quizchef.quiz.infrastructure.persistence.QuizSpecifications;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of quiz authoring. Owners see their quizzes in any state;
 * everyone else sees only non-private quizzes, and private quizzes of
 * other owners are indistinguishable from missing ones (404).
 */
@Service
public class QuizQueryService {

    private final QuizRepository quizRepository;
    private final AuthorizationService authorizationService;

    public QuizQueryService(QuizRepository quizRepository,
                            AuthorizationService authorizationService) {
        this.quizRepository = quizRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public QuizView quiz(CurrentUser currentUser, UUID quizId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_VIEW);
        Quiz quiz = quizRepository.findById(quizId)
                .orElseThrow(() -> new QuizNotFoundException(quizId));
        QuizOwnership.requireViewable(currentUser, quiz);
        return QuizView.of(quiz);
    }

    /**
     * The caller's own quizzes, filtered and paged — "My Quizzes". There is
     * no cross-author listing (PRD v1.1 question bank territory, and the
     * spec is explicit: no generic "list all" endpoint) — this is
     * deliberately owner-scoped only, enforced by the specification itself,
     * not by a post-filter.
     */
    @Transactional(readOnly = true)
    public Page<QuizSummaryView> mine(CurrentUser currentUser, QuizSearchQuery filter, Pageable pageable) {
        authorizationService.authorize(currentUser, Permission.QUIZ_VIEW);
        SortProperties.validate(pageable.getSort());

        Specification<Quiz> specification = Specification
                .where(QuizSpecifications.ownedBy(currentUser.identityId()))
                .and(QuizSpecifications.hasState(filter.state()))
                .and(QuizSpecifications.titleContains(filter.search()));

        return quizRepository.findAll(specification, pageable).map(QuizSummaryView::of);
    }
}
