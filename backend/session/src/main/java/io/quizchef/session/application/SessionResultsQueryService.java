package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.session.domain.FinalPlacementPolicy;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
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
 * Readable from the moment the answer is revealed, through the leaderboard,
 * and forever after FINISHED — except {@code personalResult} on the quiz's
 * last question, which stays hidden until the host's final-results release
 * regardless of whether the session has technically finished yet (see
 * {@link #personalResultReadable}).
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
                FinalPlacementPolicy.exactRankRevealCount(entries),
                entries);
    }

    /**
     * One participant's own progress: the points the question in play
     * awarded them, their total, and the framing counts — no other
     * participant's anything, and <strong>no rank of their own</strong>.
     *
     * <p>This read does not rank at all. It could have ranked the roster
     * and then declined to return the position, but not computing it is
     * the stronger guarantee: there is no rank in scope to leak through a
     * later edit, a log line, or a field added in haste. Where a
     * participant finished is
     * {@link ParticipantFinalPlacementQueryService}'s to say, after the
     * host's release.
     */
    @Transactional(readOnly = true)
    public ParticipantResultView personalResult(UUID sessionId, UUID participantId) {
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        if (!personalResultReadable(session)) {
            throw new ResultsNotAvailableException();
        }
        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        if (session.getState() == SessionState.IN_PROGRESS && isLastQuestion(session, quiz)) {
            throw new ResultsNotAvailableException();
        }

        Participant own = participantRepository.findBySessionId(sessionId).stream()
                .filter(participant -> participant.getId().equals(participantId))
                .findFirst()
                .orElseThrow(ParticipantNotFoundException::new);
        int pointsEarned = own.answers().stream()
                .filter(answer -> answer.questionId().equals(session.getCurrentQuestionId()))
                .findFirst()
                .map(ParticipantAnswer::pointsAwarded)
                .orElse(0);

        return new ParticipantResultView(
                session.getId(),
                session.getState(),
                session.getCurrentPhase(),
                quiz.questions().size(),
                session.participantCount(),
                own.getId(),
                own.getDisplayName(),
                own.getTotalScore(),
                pointsEarned);
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

    /**
     * Narrower than {@link #resultsReadable}: once the session has finished,
     * a participant's own final rank stays hidden until the host explicitly
     * releases it (the winner-ceremony hold) — the host's own full standings
     * read is unaffected, since the host needs them to run the ceremony
     * before clicking release. While still {@code IN_PROGRESS}, the caller
     * additionally checks {@link #isLastQuestion}: the quiz's last question
     * holds its standings for that same ceremony, so a participant must not
     * learn their final rank just because the session hasn't flipped to
     * {@code FINISHED} yet — the identical rule
     * {@link ParticipantRankContextQueryService} already applies to ranking
     * neighbours.
     */
    private static boolean personalResultReadable(Session session) {
        if (session.getState() == SessionState.FINISHED
                || session.getState() == SessionState.ARCHIVED) {
            return session.isFinalResultsReleased();
        }
        return session.getState() == SessionState.IN_PROGRESS
                && (session.getCurrentPhase() == SessionPhase.ANSWER_REVEALED
                        || session.getCurrentPhase() == SessionPhase.LEADERBOARD);
    }

    private static boolean isLastQuestion(Session session, PlayableQuizView quiz) {
        return QuestionProgression.nextAfter(quiz, session.getCurrentQuestionId()).isEmpty();
    }
}
