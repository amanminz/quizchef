package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.LobbyOpenedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens a session's lobby (CREATED → LOBBY) so participants may join. Host
 * only: the caller must hold {@code QUIZ_HOST} and be this session's host.
 */
@Service
public class OpenLobbyApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OpenLobbyApplicationService.class);

    private final SessionRepository sessionRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public OpenLobbyApplicationService(SessionRepository sessionRepository,
                                       AuthorizationService authorizationService,
                                       DomainEventPublisher eventPublisher,
                                       Clock clock) {
        this.sessionRepository = sessionRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView openLobby(CurrentUser currentUser, String sessionPin) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.activeByPin(sessionRepository, sessionPin);
        SessionHostPolicy.requireHost(currentUser, session);

        session.openLobby();
        sessionRepository.saveAndFlush(session);

        eventPublisher.publish(new LobbyOpenedEvent(session.getId(), clock.instant()));
        log.info("Lobby opened for session {}", session.getId());
        return SessionSummaryView.of(session);
    }
}
