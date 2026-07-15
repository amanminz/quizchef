package io.quizchef.quiz.infrastructure.persistence;

import io.quizchef.quiz.domain.Tag;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, UUID> {

    Optional<Tag> findByName(String name);
}
