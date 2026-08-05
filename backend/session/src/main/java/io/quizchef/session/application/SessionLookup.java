package io.quizchef.session.application;

import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.SessionNotFoundException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.UUID;

/**
 * Finds sessions the two ways the orchestration needs them — by PIN (the
 * public join key: the one active session for that code) and by id (the
 * host's internal handle) — raising a uniform 404 when absent.
 */
final class SessionLookup {

    private SessionLookup() {
    }

    static Session activeByPin(SessionRepository repository, String pin) {
        return repository.findBySessionPinValueAndStateNot(pin, SessionState.ARCHIVED)
                .orElseThrow(() -> new SessionNotFoundException("No active session for PIN " + pin));
    }

    /**
     * The same session, write-locked for the rest of the transaction — for
     * joining, which appends to the roster and therefore has every device in
     * the room contending for one row. See
     * {@link SessionRepository#findAndLockBySessionPinValueAndStateNot}.
     */
    static Session activeByPinForUpdate(SessionRepository repository, String pin) {
        return repository.findAndLockBySessionPinValueAndStateNot(pin, SessionState.ARCHIVED)
                .orElseThrow(() -> new SessionNotFoundException("No active session for PIN " + pin));
    }

    static Session byId(SessionRepository repository, UUID sessionId) {
        return repository.findById(sessionId)
                .orElseThrow(() -> new SessionNotFoundException("Session %s does not exist".formatted(sessionId)));
    }
}
