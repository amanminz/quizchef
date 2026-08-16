package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.SessionQuestionCorrection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionQuestionCorrectionRepository
        extends JpaRepository<SessionQuestionCorrection, UUID> {

    /**
     * Every correction this session is playing. Read whole rather than per
     * question: the effective quiz is assembled in one pass, and a session
     * has at most a handful of corrections.
     */
    List<SessionQuestionCorrection> findBySessionId(UUID sessionId);

    /** At most one — a session corrects a question in place, never twice over. */
    Optional<SessionQuestionCorrection> findBySessionIdAndQuestionId(UUID sessionId, UUID questionId);
}
