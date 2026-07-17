package io.quizchef.quiz.application;

import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Guards which properties a list/search endpoint may sort by.
 *
 * <p>Sorting is applied directly against the aggregate root's own columns
 * (a JPA {@link Sort} translates to {@code ORDER BY} on the queried entity).
 * Content like a quiz or question's title lives in a per-language child
 * collection, not a root column, so it is deliberately not sortable —
 * "which language's title?" has no single answer, and a request to sort by
 * it would either fail or silently do nothing. Requesting an unsupported
 * property is rejected (400) rather than ignored, so a client learns
 * immediately rather than shipping a silently-wrong sort.
 */
final class SortProperties {

    static final Set<String> ALLOWED = Set.of("updatedAt", "createdAt", "state");

    private SortProperties() {
    }

    static void validate(Sort sort) {
        sort.forEach(order -> {
            if (!ALLOWED.contains(order.getProperty())) {
                throw new IllegalArgumentException(
                        "Cannot sort by '%s'; supported: %s".formatted(order.getProperty(), ALLOWED));
            }
        });
    }
}
