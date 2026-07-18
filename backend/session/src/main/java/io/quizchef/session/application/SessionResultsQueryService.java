package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.ParticipantNotFoundException;
import io.quizchef.session.domain.exception.ResultsNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of results: the current standings, projected fresh from the
 * participants' cached scores (never stored — ADR-006), with no phase
 * transition and no event. The command counterpart is
 * {@link ShowLeaderboardApplicationService}, which the host uses to *move*
 * the game to its leaderboard step and broadcast it; this read exists so a
 * refreshed or late-arriving client can recover the same standings the
 * broadcast carried — the host command is not re-issuable (it would 409 on
 * its phase guard), and events are gone once missed.
 *
 * <p>Visibility is role-scoped (live-event privacy): the full standings —
 * every name, score, and rank — are the host's projection and require the
 * hosting identity; a participant device reads only its own row through
 * {@link #personalResult}, public like the summary and current-question
 * reads (the unguessable session and participant ids gate it — the same
 * trust answer submission already places in the participant id; the
 * audience includes anonymous guests). Both reads are phase-gated for the
 * same ADR-006 reason correctness is: standings during an open or merely
 * closed question would leak who answered correctly before the reveal.
 * Readable from the moment the answer is revealed, through the
 * leaderboard, and forever after FINISHED.
 */
@Service
public class SessionResultsQueryService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final LeaderboardService leaderboardService;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;

    public SessionResultsQueryService(SessionRepository sessionRepository,
                                      ParticipantRepository participantRepository,
                                      LeaderboardService leaderboardService,
                                      GameplayQuizQuery gameplayQuizQuery,
                                      AuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.leaderboardService = leaderboardService;
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public SessionResultsView results(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);
        if (!resultsReadable(session)) {
            throw new ResultsNotAvailableException();
        }

        List<LeaderboardEntry> entries = leaderboardService.rank(
                participantRepository.findBySessionId(sessionId), session.roster());
        int totalQuestions = gameplayQuizQuery.load(session.getPublishedQuizVersionId())
                .questions().size();

        return new SessionResultsView(
                session.getId(),
                session.getState(),
                session.getCurrentPhase(),
                totalQuestions,
                session.participantCount(),
                entries);
    }

    /**
     * One participant's own row — rank, score, and the framing counts —
     * with no other participant's name, score, or rank in the response.
     * The ranking itself is still computed over the whole roster (a rank
     * is meaningless otherwise); only the projection narrows.
     */
    @Transactional(readOnly = true)
    public ParticipantResultView personalResult(UUID sessionId, UUID participantId) {
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        if (!resultsReadable(session)) {
            throw new ResultsNotAvailableException();
        }

        List<LeaderboardEntry> entries = leaderboardService.rank(
                participantRepository.findBySessionId(sessionId), session.roster());
        LeaderboardEntry own = entries.stream()
                .filter(entry -> entry.participantId().equals(participantId))
                .findFirst()
                .orElseThrow(ParticipantNotFoundException::new);
        int totalQuestions = gameplayQuizQuery.load(session.getPublishedQuizVersionId())
                .questions().size();

        return new ParticipantResultView(
                session.getId(),
                session.getState(),
                session.getCurrentPhase(),
                totalQuestions,
                session.participantCount(),
                own);
    }

    private static boolean resultsReadable(Session session) {
        if (session.getState() == SessionState.FINISHED
                || session.getState() == SessionState.ARCHIVED) {
            return true;
        }
        return session.getState() == SessionState.IN_PROGRESS
                && (session.getCurrentPhase() == SessionPhase.ANSWER_REVEALED
                        || session.getCurrentPhase() == SessionPhase.LEADERBOARD);
    }
}
