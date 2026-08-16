package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantRecoveryCode;
import io.quizchef.session.domain.RecoveryCode;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.domain.exception.RecoveryNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRecoveryCodeRepository;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mints a short-lived code the host reads out so one stranded player can get
 * back in. Host only.
 *
 * <p>This exists because the resume token is the <em>only</em> proof an
 * anonymous player has, so losing browser storage leaves them able to prove
 * nothing — and letting them back in on their name would hand their score to
 * anyone who heard it. The missing authority is supplied by the host, who
 * can see the person asking.
 *
 * <p>Issuing supersedes any code already outstanding for that participant. A
 * host who clicks twice, or misreads the first code, must not leave two live
 * codes for one player.
 */
@Service
public class GenerateRecoveryCodeApplicationService {

    private static final Logger log = LoggerFactory.getLogger(GenerateRecoveryCodeApplicationService.class);

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantRecoveryCodeRepository recoveryCodeRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public GenerateRecoveryCodeApplicationService(SessionRepository sessionRepository,
                                                  ParticipantRepository participantRepository,
                                                  ParticipantRecoveryCodeRepository recoveryCodeRepository,
                                                  AuthorizationService authorizationService,
                                                  Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    @Transactional
    public RecoveryCodeView generate(CurrentUser currentUser, UUID sessionId, UUID participantId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        Participant participant = participantRepository.findById(participantId)
                .filter(candidate -> candidate.getSessionId().equals(sessionId))
                .orElseThrow(ParticipantNotFoundException::new);
        if (!participant.isGuest()) {
            // A registered player resumes on their account from any device.
            // Handing them a code would be a second, weaker way in.
            throw new RecoveryNotAvailableException(
                    "This player is signed in and can rejoin from any device by logging in");
        }
        if (session.isFinished() || session.isArchived()) {
            throw new RecoveryNotAvailableException("This quiz has finished");
        }

        Instant now = clock.instant();
        recoveryCodeRepository.supersedeOutstanding(participantId, now);
        RecoveryCode code = RecoveryCode.generate();
        recoveryCodeRepository.saveAndFlush(
                ParticipantRecoveryCode.issue(sessionId, participantId, code, now));

        // The participant, never the code: the digits are a live credential
        // for the next few minutes.
        log.info("participant.recovery.code_issued session={} participant={}",
                sessionId, participantId);
        return new RecoveryCodeView(participantId, participant.getDisplayName(), code.value(),
                now.plus(ParticipantRecoveryCode.LIFETIME));
    }
}
