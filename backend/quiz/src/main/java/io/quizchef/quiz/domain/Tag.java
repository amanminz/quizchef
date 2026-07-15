package io.quizchef.quiz.domain;

import io.quizchef.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A shared, language-independent label for questions. Deliberately its own
 * aggregate — questions hold tag ids, not strings — so synonyms,
 * descriptions, hierarchies, usage counts, and organization vocabularies
 * can grow on the tag without ever touching the Question model.
 *
 * <p>Names are normalized (trimmed, lowercase) so "Moses" and "moses" are
 * the same tag; the unique index on name is the authority under
 * concurrency.
 */
@Entity
@Table(name = "tags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag extends AuditableEntity {

    static final int MAX_NAME_LENGTH = 50;

    @Column(nullable = false, unique = true, length = MAX_NAME_LENGTH)
    private String name;

    private Tag(UUID id, String name) {
        super(id);
        this.name = name;
    }

    public static Tag named(String name) {
        return new Tag(UUID.randomUUID(), normalize(name));
    }

    /**
     * The canonical form of a tag name — what equality and the unique
     * index operate on.
     */
    public static String normalize(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("tag name must not be blank");
        }
        String normalized = name.strip().toLowerCase(Locale.ROOT);
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "tag name must not exceed %d characters".formatted(MAX_NAME_LENGTH));
        }
        return normalized;
    }
}
