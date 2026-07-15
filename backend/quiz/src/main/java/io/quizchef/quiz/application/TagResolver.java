package io.quizchef.quiz.application;

import io.quizchef.common.exception.ConflictException;
import io.quizchef.quiz.domain.Tag;
import io.quizchef.quiz.infrastructure.persistence.TagRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Turns tag names into Tag aggregates, creating unknown ones on the fly —
 * the question APIs accept names, the Question aggregate stores ids.
 * Names are normalized by the Tag aggregate, so "Moses" and " moses "
 * resolve to the same tag.
 */
@Service
public class TagResolver {

    private final TagRepository tagRepository;

    public TagResolver(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    /**
     * Resolves within the caller's transaction. The unique index on the
     * tag name is the authority: if another transaction creates the same
     * new tag concurrently, this one surfaces a retryable conflict rather
     * than corrupting anything.
     */
    public List<Tag> resolve(List<String> names) {
        Set<String> normalized = new LinkedHashSet<>();
        names.forEach(name -> normalized.add(Tag.normalize(name)));

        List<Tag> tags = new ArrayList<>();
        for (String name : normalized) {
            tags.add(tagRepository.findByName(name).orElseGet(() -> createTag(name)));
        }
        return tags;
    }

    private Tag createTag(String name) {
        try {
            return tagRepository.saveAndFlush(Tag.named(name));
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("tag.concurrent-creation",
                    "Tag '%s' was created concurrently. Retry the request.".formatted(name));
        }
    }
}
