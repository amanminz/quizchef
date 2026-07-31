package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.FinalResultsReleasedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Releases final standings to participants. Host only, and the explicit
 * counterpart to the finish-then-hold flow: a session finishes without
 * disclosing final rank so the host can run a winner ceremony first, and
 * this is the one command that lifts the hold. Idempotent by construction
 * ({@link Session#releaseFinalResults()} no-ops once already released), so
 * a duplicate click or a retried request is harmless rather than a
 * conflict.
 */
@Service
public class ReleaseFinalResultsApplicationService {

    private final SessionRepository sessionRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ReleaseFinalResultsApplicationService(SessionRepository sessionRepository,
                                                 AuthorizationService authorizationService,
                                                 DomainEventPublisher eventPublisher,
                                                 Clock clock) {
        this.sessionRepository = sessionRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView release(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        boolean wasAlreadyReleased = session.isFinalResultsReleased();
        session.releaseFinalResults();
        sessionRepository.saveAndFlush(session);

        if (!wasAlreadyReleased) {
            eventPublisher.publish(new FinalResultsReleasedEvent(sessionId, clock.instant()));
        }
        return SessionSummaryView.of(session);
    }
}
