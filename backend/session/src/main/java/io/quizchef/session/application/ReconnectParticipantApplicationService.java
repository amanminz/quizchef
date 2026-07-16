package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reconnects a participant to their session, preserving their identity,
 * score, and answers (ADR-003). A guest presents the token issued on join
 * (globally unique); a registered player is resolved from their authenticated
 * identity within the given session — display names and connections never
 * identify anyone.
 *
 * <p>Returns the reconnection snapshot (RFC-005 replay contract), simply
 * populated for now: in the lobby there is no question, timer, or score to
 * restore. The richer snapshot arrives with gameplay and {@code
 * SessionRecoveryService}; the contract is already the final one.
 */
@Service
public class ReconnectParticipantApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ReconnectParticipantApplicationService.class);

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ReconnectParticipantApplicationService(SessionRepository sessionRepository,
                                                  ParticipantRepository participantRepository,
                                                  DomainEventPublisher eventPublisher,
                                                  Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSnapshotView reconnect(CurrentUser currentUser, ReconnectSessionCommand command) {
        Participant participant = findParticipant(currentUser, command);
        Session session = SessionLookup.byId(sessionRepository, participant.getSessionId());

        participant.connect(clock.instant());
        participantRepository.save(participant);

        eventPublisher.publish(new ParticipantReconnectedEvent(
                session.getId(), participant.getId(), clock.instant()));
        log.info("Participant {} reconnected to session {}", participant.getId(), session.getId());
        return SessionSnapshotView.of(session, participant);
    }

    private Participant findParticipant(CurrentUser currentUser, ReconnectSessionCommand command) {
        String guestToken = command.guestParticipantToken();
        if (guestToken != null && !guestToken.isBlank()) {
            return participantRepository.findByGuestParticipantTokenValue(guestToken)
                    .orElseThrow(ParticipantNotFoundException::new);
        }
        if (!currentUser.authenticated()) {
            throw new UnauthorizedException();
        }
        if (command.sessionId() == null) {
            throw new IllegalArgumentException(
                    "sessionId is required to reconnect a registered participant");
        }
        return participantRepository.findBySessionIdAndIdentityReferenceIdentityId(
                        command.sessionId(), currentUser.identityId())
                .orElseThrow(ParticipantNotFoundException::new);
    }
}
