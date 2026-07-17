package io.quizchef.quiz.infrastructure.persistence;

import io.quizchef.quiz.domain.Quiz;
import io.quizchef.quiz.domain.QuizLocalization;
import io.quizchef.quiz.domain.QuizState;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

/**
 * Optional, composable filter predicates for "my quizzes" (RFC-003). Each
 * factory returns {@code null} for an absent filter — {@link Specification}
 * composition (`.and(null)`) skips it — so the caller builds one query from
 * only the predicates actually requested, never loading or filtering more
 * than asked.
 */
public final class QuizSpecifications {

    private QuizSpecifications() {
    }

    public static Specification<Quiz> ownedBy(UUID ownerId) {
        return (root, query, cb) -> cb.equal(root.get("ownerIdentity").get("identityId"), ownerId);
    }

    public static Specification<Quiz> hasState(QuizState state) {
        if (state == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("state"), state);
    }

    /**
     * Case-insensitive title match against any of the quiz's localizations,
     * not only the default language. Joining a to-many collection can
     * multiply rows, so {@code distinct} is set on the query — Spring Data
     * carries it through to both the content and the count query.
     */
    public static Specification<Quiz> titleContains(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        String needle = "%" + search.strip().toLowerCase(Locale.ROOT) + "%";
        return (root, query, cb) -> {
            query.distinct(true);
            Join<Quiz, QuizLocalization> localizations = root.join("localizations", JoinType.LEFT);
            return cb.like(cb.lower(localizations.get("title")), needle);
        };
    }
}
