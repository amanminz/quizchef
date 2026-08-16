package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.session.domain.GuestParticipantToken;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantKey;
import io.quizchef.session.domain.ParticipantRecoveryCode;
import io.quizchef.session.domain.RecoveryCode;
import io.quizchef.session.domain.RecoveryCodeDigest;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.ParticipantReconnectedEvent;
import io.quizchef.session.domain.exception.RecoveryCodeNotAcceptedException;
import io.quizchef.session.infrastructure.persistence.ParticipantRecoveryCodeRepository;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Puts a stranded player back into their own participant, on the host's
 * authority, and gives their new device a fresh resume credential.
 *
 * <p>The code is the proof, and it is spent doing it. Redemption is
 * single-use by construction: the whole thing runs under the session's
 * write lock, and marking the code used happens in the same transaction as
 * issuing the new token — so two people racing with the same digits produce
 * one recovery and one refusal, never two participants or two live tokens.
 *
 * <p><strong>The old resume token is rotated, not reused.</strong> The
 * device that lost the game may be lost, borrowed, or someone else's; once
 * recovery happens it must stop being able to resume. Rotating means moving
 * the digest in two places — the participant, and the session roster's
 * mirror of it — which is why the session is loaded and saved here at all.
 *
 * <p>Everything else about the participant is untouched: same id, same
 * name, same language, same answers, same score. Recovery changes who is
 * holding the credential, never who the player is.
 */
@Service
public class RedeemRecoveryCodeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(RedeemRecoveryCodeApplicationService.class);

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantRecoveryCodeRepository recoveryCodeRepository;
    private final SessionSnapshotAssembler snapshotAssembler;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RedeemRecoveryCodeApplicationService(SessionRepository sessionRepository,
                                                ParticipantRepository participantRepository,
                                                ParticipantRecoveryCodeRepository recoveryCodeRepository,
                                                SessionSnapshotAssembler snapshotAssembler,
                                                DomainEventPublisher eventPublisher,
                                                Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.snapshotAssembler = snapshotAssembler;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public RecoveredParticipantView redeem(String sessionPin, String presentedCode) {
        Session session = SessionLookup.activeByPinForUpdate(sessionRepository, sessionPin);
        RecoveryCode code = parse(presentedCode);
        Instant now = clock.instant();

        ParticipantRecoveryCode issued = recoveryCodeRepository
                .findBySessionIdAndCodeDigestValue(session.getId(), RecoveryCodeDigest.of(code).value())
                .orElseThrow(RecoveryCodeNotAcceptedException::new);
        // Verified again in the domain, in constant time, for the same
        // reason resume does: the decision belongs somewhere a reader can
        // find it, not implicitly in a WHERE clause.
        if (!issued.matches(code) || !issued.redeem(now)) {
            logFailure(session, "code_invalid");
            throw new RecoveryCodeNotAcceptedException();
        }
        recoveryCodeRepository.saveAndFlush(issued);

        Participant participant = participantRepository.findById(issued.getParticipantId())
                .filter(candidate -> candidate.getSessionId().equals(session.getId()))
                .orElseThrow(RecoveryCodeNotAcceptedException::new);

        GuestParticipantToken replacement = GuestParticipantToken.generate();
        participant.rotateResumeToken(replacement);
        session.rekeyParticipant(participant.getId(), ParticipantKey.forGuest(replacement));
        participant.connect(now);
        participantRepository.save(participant);
        sessionRepository.saveAndFlush(session);

        eventPublisher.publish(new ParticipantReconnectedEvent(
                session.getId(), participant.getId(), now));
        log.info("participant.recovery.success session={} participant={}",
                session.getId(), participant.getId());
        return new RecoveredParticipantView(
                replacement.value(), snapshotAssembler.assemble(session, participant));
    }

    /**
     * A malformed code is refused exactly like a wrong one. Telling the
     * caller their digits were the wrong shape would let them map the code
     * space for free.
     */
    private RecoveryCode parse(String presentedCode) {
        try {
            return RecoveryCode.of(presentedCode);
        } catch (RuntimeException malformed) {
            throw new RecoveryCodeNotAcceptedException();
        }
    }

    private void logFailure(Session session, String reason) {
        log.info("participant.recovery.failed session={} reason={}", session.getId(), reason);
    }
}
