package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.quiz.domain.LanguageCode;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.ParticipantJoinedEvent;
import io.quizchef.session.domain.exception.ParticipantAlreadyJoinedException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Joins a session by PIN — the one flow guests and registered players share
 * (ADR-003: guest play and registered play are one code path). No
 * authorization: participants are anonymous-friendly and guests are
 * first-class.
 *
 * <p>A registered caller becomes a participant backed by their identity; an
 * anonymous caller becomes a guest issued a fresh reconnection token. The
 * Session aggregate decides whether the join is allowed (lobby state or late
 * join), whether the session is full, and whether this identity/token is
 * already in it; the database unique constraints are the backstop for a
 * same-instant race.
 */
@Service
public class JoinSessionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(JoinSessionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public JoinSessionApplicationService(SessionRepository sessionRepository,
                                         ParticipantRepository participantRepository,
                                         DomainEventPublisher eventPublisher,
                                         Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public ParticipantSessionView join(CurrentUser currentUser, JoinSessionCommand command) {
        Session session = SessionLookup.activeByPin(sessionRepository, command.sessionPin());
        LanguageCode language = LanguageCode.of(command.preferredLanguage());

        Participant participant = currentUser.authenticated()
                ? Participant.registered(session.getId(), currentUser.reference(),
                        command.displayName(), language)
                : Participant.guest(session.getId(), GuestParticipantToken.generate(),
                        command.displayName(), language);

        // The aggregate enforces state, capacity, and uniqueness before anything persists.
        session.registerParticipant(participant.getId(), participant.key());
        try {
            participantRepository.save(participant);
            sessionRepository.saveAndFlush(session);
        } catch (DataIntegrityViolationException raced) {
            throw new ParticipantAlreadyJoinedException();
        }

        eventPublisher.publish(new ParticipantJoinedEvent(
                session.getId(), participant.getId(), clock.instant()));
        log.info("Participant {} ({}) joined session {}", participant.getId(),
                participant.isGuest() ? "guest" : "registered", session.getId());
        return ParticipantSessionView.of(participant, session.getState());
    }
}
