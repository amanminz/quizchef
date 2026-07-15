package io.quizchef.quiz.infrastructure.persistence;

import io.quizchef.quiz.domain.Question;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuestionRepository extends JpaRepository<Question, UUID> {
}
