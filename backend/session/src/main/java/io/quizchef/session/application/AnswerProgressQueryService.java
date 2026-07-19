package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of live answer progress: the authoritative "5 / 10 answered"
 * the host screen shows while a question is open. Host only — who is
 * still thinking is host material, exactly like the roster; participants
 * see only their own submission acknowledgement.
 *
 * <p>The counts are projected fresh from the participant aggregates, so
 * an accepted answer counts exactly once (a participant holds at most one
 * answer per question) and duplicates or rejected submissions never
 * count. Eligible means "could answer right now": every connected
 * participant, plus anyone whose answer is already in even if they have
 * since dropped — so the answered count can never exceed the eligible
 * count, and a late joiner grows the denominator the moment they connect.
 */
@Service
public class AnswerProgressQueryService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final AuthorizationService authorizationService;

    public AnswerProgressQueryService(SessionRepository sessionRepository,
                                      ParticipantRepository participantRepository,
                                      AuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public AnswerProgressView progress(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        UUID questionId = session.getCurrentQuestionId();
        if (session.getState() != SessionState.IN_PROGRESS || questionId == null) {
            throw new NoCurrentQuestionException(session.getState());
        }

        List<Participant> participants = participantRepository.findBySessionId(sessionId);
        int answered = 0;
        int eligible = 0;
        for (Participant participant : participants) {
            boolean hasAnswered = participant.answers().stream()
                    .anyMatch(answer -> answer.questionId().equals(questionId));
            if (hasAnswered) {
                answered++;
                eligible++;
            } else if (participant.isConnected()) {
                eligible++;
            }
        }
        return new AnswerProgressView(sessionId, questionId, answered, eligible);
    }
}
