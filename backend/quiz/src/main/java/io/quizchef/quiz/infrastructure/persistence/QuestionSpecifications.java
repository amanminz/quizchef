package io.quizchef.quiz.infrastructure.persistence;

import io.quizchef.quiz.domain.Difficulty;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.quiz.domain.Question;
import io.quizchef.quiz.domain.QuestionLocalization;
import io.quizchef.quiz.domain.QuestionState;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Optional, composable filter predicates for the question library search
 * (RFC-003). Each factory returns {@code null} for an absent filter —
 * {@link Specification} composition skips it — so the query built matches
 * exactly the filters the caller supplied.
 */
public final class QuestionSpecifications {

    private QuestionSpecifications() {
    }

    public static Specification<Question> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerIdentity").get("identityId"), ownerId);
    }

    public static Specification<Question> hasState(QuestionState state) {
        if (state == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("state"), state);
    }

    public static Specification<Question> hasDifficulty(Difficulty difficulty) {
        if (difficulty == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("difficulty"), difficulty);
    }

    /**
     * At least one localization exists for the given language.
     */
    public static Specification<Question> hasLanguage(LanguageCode language) {
        if (language == null) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Question, QuestionLocalization> localizations = root.join("localizations", JoinType.LEFT);
            return cb.equal(localizations.get("languageCode").get("value"), language.value());
        };
    }

    /**
     * Matches a question tagged with at least one of the given tags — an
     * OR within the facet, the conventional tag-filter semantics.
     */
    public static Specification<Question> hasAnyTag(List<UUID> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Question, UUID> tags = root.join("tagIds", JoinType.LEFT);
            return tags.in(tagIds);
        };
    }

    /**
     * Case-insensitive match against the title or prompt of any
     * localization, not only the default language — an author searching in
     * Kannada should find a question by its Kannada translation.
     */
    public static Specification<Question> matchesText(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String needle = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Question, QuestionLocalization> localizations = root.join("localizations", JoinType.LEFT);
            Predicate titleMatch = cb.like(cb.lower(localizations.get("title")), needle);
            Predicate promptMatch = cb.like(cb.lower(localizations.get("prompt")), needle);
            return cb.or(titleMatch, promptMatch);
        };
    }
}
