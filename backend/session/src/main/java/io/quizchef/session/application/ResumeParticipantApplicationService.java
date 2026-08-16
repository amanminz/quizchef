package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.common.exception.UnauthorizedException;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.GuestTokenDigest;
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
 * Returns a player to the session they were already in — the same
 * participant, with their score, their answers, their name, and their
 * language (ADR-003).
 *
 * <p>Called on every arrival, not only after a visible disconnect: a
 * refresh, a reopened tab, a phone that slept through two questions, and a
 * dropped WebSocket are indistinguishable from the server's side and all
 * mean the same thing. This runs <em>before</em> any join, so a returning
 * player is never offered the join form they would otherwise fill in
 * again — which is how a second participant with nobody's score gets
 * created.
 *
 * <p><strong>Identity comes from the token, never the name.</strong> A
 * guest presents the secret issued at join and the server compares its
 * digest; a registered player is resolved from their authenticated
 * identity. Nothing here reads a display name, because "same name" and
 * "same person" are different claims and only one of them can be proved.
 *
 * <p><strong>Scoped to one session, resolved from the PIN.</strong> The
 * session is looked up the way {@link JoinSessionApplicationService} looks
 * it up — the <em>active</em> session for that PIN — and the participant
 * must be in that session. Both halves matter. PINs are reused once a
 * session is archived, so a player returning to a familiar code may be
 * holding a credential for a quiz that has already finished; resolving from
 * the PIN means they are told they are not in this one and can join it,
 * rather than being silently restored into last week's game. And a token
 * that belongs to a different live session simply does not resolve here,
 * so it cannot be replayed across quizzes.
 */
@Service
public class ResumeParticipantApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ResumeParticipantApplicationService.class);

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final SessionSnapshotAssembler snapshotAssembler;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ResumeParticipantApplicationService(SessionRepository sessionRepository,
                                               ParticipantRepository participantRepository,
                                               SessionSnapshotAssembler snapshotAssembler,
                                               DomainEventPublisher eventPublisher,
                                               Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.snapshotAssembler = snapshotAssembler;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSnapshotView resume(CurrentUser currentUser, ResumeParticipantCommand command) {
        // The session row is write-locked for the same reason joining locks
        // it: two tabs, or a reconnect racing a refresh, both resume the
        // same participant at the same instant. Serialized, they converge on
        // one connected participant; unserialized, one of them loses an
        // optimistic check and the player sees an error for doing nothing
        // wrong.
        Session session = SessionLookup.activeByPinForUpdate(sessionRepository, command.sessionPin());
        Participant participant = findParticipant(currentUser, session, command);

        // Idempotent by construction (Participant.connect accepts an already
        // connected participant), so a duplicate resume changes nothing —
        // no second roster entry, no reset score, and the session's own
        // phase is never written here at all, so a resume racing a question
        // transition cannot roll the game backwards.
        participant.connect(clock.instant());
        participantRepository.save(participant);

        eventPublisher.publish(new ParticipantReconnectedEvent(
                session.getId(), participant.getId(), clock.instant()));
        log.info("Participant {} resumed session {}", participant.getId(), session.getId());
        return snapshotAssembler.assemble(session, participant);
    }

    /**
     * Resolves who is resuming, and refuses every weaker claim.
     *
     * <p>A participant id is deliberately not accepted as proof. It travels
     * in URLs and in this session's own API responses, so treating it as a
     * credential would let anyone who saw one take over that player's score.
     * It identifies; the token authenticates.
     */
    private Participant findParticipant(CurrentUser currentUser, Session session,
                                        ResumeParticipantCommand command) {
        String presented = command.resumeToken();
        if (presented != null && !presented.isBlank()) {
            GuestParticipantToken token = GuestParticipantToken.of(presented);
            Participant participant = participantRepository
                    .findBySessionIdAndGuestTokenDigestValue(
                            session.getId(), GuestTokenDigest.of(token).value())
                    .orElseThrow(ParticipantNotFoundException::new);
            // The lookup already matched on digest; verifying again in the
            // domain keeps the authorization decision somewhere a reader can
            // find it, and keeps it constant-time wherever it is made.
            if (!participant.matchesResumeToken(token)) {
                throw new ParticipantNotFoundException();
            }
            return participant;
        }
        if (!currentUser.authenticated()) {
            throw new UnauthorizedException();
        }
        return participantRepository.findBySessionIdAndIdentityReferenceIdentityId(
                        session.getId(), currentUser.identityId())
                .orElseThrow(ParticipantNotFoundException::new);
    }
}
