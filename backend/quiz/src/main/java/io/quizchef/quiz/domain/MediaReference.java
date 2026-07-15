package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.util.Objects;
import java.util.UUID;

/**
 * A pointer to media in object storage. Only the reference is modeled —
 * uploads and downloads belong to the media module (RFC-007).
 *
 * @param altText optional accessibility text
 */
@Embeddable
public record MediaReference(
        UUID id,
        @Enumerated(EnumType.STRING) MediaType mediaType,
        String storageKey,
        String altText,
        int displayOrder
) {

    public MediaReference {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(mediaType, "mediaType must not be null");
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank");
        }
        storageKey = storageKey.strip();
        if (displayOrder < 1) {
            throw new IllegalArgumentException("displayOrder must be positive");
        }
    }

    public static MediaReference of(MediaType mediaType, String storageKey, String altText, int displayOrder) {
        return new MediaReference(UUID.randomUUID(), mediaType, storageKey, altText, displayOrder);
    }
}
