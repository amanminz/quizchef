package io.quizchef.quiz.infrastructure.persistence;

import io.quizchef.quiz.domain.Quiz;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuizRepository extends JpaRepository<Quiz, UUID> {
}
