package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.SessionStartedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Starts a session (LOBBY → IN_PROGRESS). Host only. This PR moves the state
 * and announces it — no question is opened, no timer starts; that is the
 * gameplay engine (PR #3).
 */
@Service
public class StartSessionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StartSessionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public StartSessionApplicationService(SessionRepository sessionRepository,
                                          AuthorizationService authorizationService,
                                          DomainEventPublisher eventPublisher,
                                          Clock clock) {
        this.sessionRepository = sessionRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView start(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        session.start();
        sessionRepository.saveAndFlush(session);

        eventPublisher.publish(new SessionStartedEvent(session.getId(), clock.instant()));
        log.info("Session {} started with {} participants", session.getId(), session.participantCount());
        return SessionSummaryView.of(session);
    }
}
