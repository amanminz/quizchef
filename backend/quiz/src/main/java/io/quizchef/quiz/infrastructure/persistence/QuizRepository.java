package io.quizchef.quiz.infrastructure.persistence;

import io.quizchef.quiz.domain.Quiz;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuizRepository extends JpaRepository<Quiz, UUID>, JpaSpecificationExecutor<Quiz> {

    /** How many quizzes compose the given question — the gate safe deletion checks. */
    @Query("select count(quiz) from Quiz quiz join quiz.questions question "
            + "where question.questionId = :questionId")
    long countByQuestionId(@Param("questionId") UUID questionId);
}
