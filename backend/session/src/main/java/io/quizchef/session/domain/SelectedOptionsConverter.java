package io.quizchef.session.domain;

import jakarta.persistence.AttributeConverter;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps a {@link ParticipantAnswer}'s selected option ids to a single
 * comma-separated column. A nested collection cannot live inside an
 * embeddable that is itself in an element collection, so the set is folded
 * into one string; it stays a JPA-only mapping detail (no Spring), which
 * keeps the domain framework-independent by this codebase's convention.
 */
public class SelectedOptionsConverter implements AttributeConverter<Set<UUID>, String> {

    @Override
    public String convertToDatabaseColumn(Set<UUID> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return "";
        }
        return optionIds.stream().map(UUID::toString).collect(Collectors.joining(","));
    }

    @Override
    public Set<UUID> convertToEntityAttribute(String column) {
        if (column == null || column.isBlank()) {
            return Set.of();
        }
        Set<UUID> optionIds = new LinkedHashSet<>();
        for (String token : column.split(",")) {
            optionIds.add(UUID.fromString(token.strip()));
        }
        return optionIds;
    }
}
