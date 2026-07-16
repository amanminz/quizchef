package io.quizchef.session.infrastructure.persistence;

import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<Session, UUID> {

    /**
     * The one active (non-archived) session for a PIN, if any. A PIN is
     * unique among active sessions and reused only after archival, so this
     * returns at most one row.
     */
    Optional<Session> findBySessionPinValueAndStateNot(String sessionPinValue, SessionState state);

    boolean existsBySessionPinValueAndStateNot(String sessionPinValue, SessionState state);
}
