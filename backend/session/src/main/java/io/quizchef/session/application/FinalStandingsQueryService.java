package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.FinalStanding;
import io.quizchef.session.domain.Session;
import io.quizchef.session.infrastructure.persistence.FinalStandingRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The standings a finished session ended with — read back from history, not
 * recomputed.
 *
 * <p>Host only, and for the same reason the live standings are: every name,
 * rank, and score in here belongs to somebody else. A participant's own
 * finish comes from {@link ParticipantFinalPlacementQueryService}, which
 * applies the reveal-group policy; this read applies none of it, because
 * the host running the event is entitled to the whole field. That
 * difference is the point — it is why this cannot be the same endpoint.
 *
 * <p>Returns what was captured at completion rather than ranking anything:
 * re-ranking here would let a later change to the scoring or tie-break rule
 * rewrite the result of an event that already happened.
 */
@Service
public class FinalStandingsQueryService {

    private final SessionRepository sessionRepository;
    private final FinalStandingRepository finalStandingRepository;
    private final AuthorizationService authorizationService;

    public FinalStandingsQueryService(SessionRepository sessionRepository,
                                      FinalStandingRepository finalStandingRepository,
                                      AuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.finalStandingRepository = finalStandingRepository;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public List<FinalStanding> standings(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        // Empty for a session that has not finished, and for one that
        // finished before this snapshot existed. Both are honestly "no
        // history recorded", which the client shows as such rather than
        // reconstructing something that would look authoritative and not be.
        return finalStandingRepository.findBySessionIdOrderByFinalRankAsc(sessionId);
    }
}
