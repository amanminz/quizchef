package io.quizchef.quiz.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.Tag;
import io.quizchef.quiz.domain.exception.QuestionNotFoundException;
import io.quizchef.quiz.infrastructure.persistence.QuestionRepository;
import io.quizchef.quiz.infrastructure.persistence.QuestionSpecifications;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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

    /**
     * The caller's own question library, filtered and paged. Read-only — no
     * editing lives here (Phase 2 PR #2 scope). Ownership is enforced by the
     * specification itself, so the query never sees another author's
     * questions in the first place.
     */
    @Transactional(readOnly = true)
    public Page<QuestionSummaryView> library(CurrentUser currentUser, QuestionSearchQuery filter,
                                             Pageable pageable) {
        authorizationService.authorize(currentUser, Permission.QUIZ_VIEW);
        SortProperties.validate(pageable.getSort());

        Specification<Question> specification = Specification
                .where(QuestionSpecifications.ownedBy(currentUser.identityId()))
                .and(QuestionSpecifications.hasState(filter.state()))
                .and(QuestionSpecifications.hasDifficulty(filter.difficulty()))
                .and(QuestionSpecifications.hasLanguage(filter.language()))
                .and(QuestionSpecifications.hasAnyTag(filter.tagIds()))
                .and(QuestionSpecifications.matchesText(filter.search()));

        Page<Question> page = questionRepository.findAll(specification, pageable);

        // One batched tag lookup for the whole page rather than one per row.
        Set<UUID> tagIds = new LinkedHashSet<>();
        page.getContent().forEach(question -> tagIds.addAll(question.tagIds()));
        Map<UUID, Tag> tagsById = tagRepository.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, tag -> tag));

        return page.map(question -> QuestionSummaryView.of(question, tagsOf(question, tagsById)));
    }

    private static List<Tag> tagsOf(Question question, Map<UUID, Tag> tagsById) {
        return question.tagIds().stream().map(tagsById::get).filter(Objects::nonNull).toList();
    }
}
