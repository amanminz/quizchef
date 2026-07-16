package io.quizchef.session.application;

import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of session orchestration: the session summary by id (state,
 * host, roster size, settings) — no gameplay state.
 *
 * <p>No authorization: a session is reached by its unguessable id, and a
 * guest in the lobby (anonymous by nature) must be able to see it. The PIN,
 * not a permission, gates joining.
 */
@Service
public class SessionQueryService {

    private final SessionRepository sessionRepository;

    public SessionQueryService(SessionRepository sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    @Transactional(readOnly = true)
    public SessionSummaryView summary(UUID sessionId) {
        return SessionSummaryView.of(SessionLookup.byId(sessionRepository, sessionId));
    }
}
