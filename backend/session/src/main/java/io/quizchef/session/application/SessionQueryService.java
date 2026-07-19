package io.quizchef.session.application;

import io.quizchef.quiz.application.QuizIdentityQuery;
import io.quizchef.session.domain.Session;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of session orchestration: the session summary by id (state,
 * host, roster size, settings, and the quiz's display title) — no
 * gameplay state.
 *
 * <p>No authorization: a session is reached by its unguessable id, and a
 * guest in the lobby (anonymous by nature) must be able to see it. The PIN,
 * not a permission, gates joining. The quiz title rides along so every
 * participant screen can say which quiz this is without touching the
 * host-only quiz management API.
 */
@Service
public class SessionQueryService {

    private final SessionRepository sessionRepository;
    private final QuizIdentityQuery quizIdentityQuery;

    public SessionQueryService(SessionRepository sessionRepository,
                               QuizIdentityQuery quizIdentityQuery) {
        this.sessionRepository = sessionRepository;
        this.quizIdentityQuery = quizIdentityQuery;
    }

    @Transactional(readOnly = true)
    public SessionSummaryView summary(UUID sessionId) {
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        return SessionSummaryView.of(session,
                quizIdentityQuery.quizTitle(session.getPublishedQuizVersionId()));
    }
}
