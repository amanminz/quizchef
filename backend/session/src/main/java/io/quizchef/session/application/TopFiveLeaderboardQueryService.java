package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Participant;
import io.quizchef.session.domain.ParticipantAnswer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.domain.exception.TopFiveLeaderboardNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the host's between-questions Top 5: the authoritative
 * before-and-after standings the projected leaderboard animates between.
 * Host only, exactly like {@link SessionResultsQueryService#results} and
 * {@link AnswerDistributionQueryService} — the whole point of a Top 5
 * projection is that ranks 6 onward, and every score but their own, stay
 * off participants' devices until the host's ceremony has run.
 *
 * <p>Purpose-specific rather than a slice of the host's full standings
 * read: this response carries only five rows, and it carries the previous
 * board the full read has no notion of. The full read stays exactly as it
 * was — the podium still consumes it.
 *
 * <p>Two gates, both the same rules the rest of the engine already
 * applies:
 *
 * <ul>
 *   <li><strong>Phase</strong> — {@code ANSWER_REVEALED} or
 *       {@code LEADERBOARD} only, the identical reveal-time gate
 *       {@link AnswerDistributionQueryService} enforces: standings before
 *       the reveal would leak who answered correctly (ADR-006).</li>
 *   <li><strong>Not the last question</strong> — the quiz's last question
 *       never gets an interim leaderboard at all; its standings belong to
 *       the podium. Computed with the same
 *       {@link QuestionProgression#nextAfter} the engine uses to decide
 *       when to finish a session, so "is this the final question" has
 *       exactly one definition in the codebase and the frontend never has
 *       to infer it from an index.</li>
 * </ul>
 */
@Service
public class TopFiveLeaderboardQueryService {

    /** The projected board shows five rows at most (fewer in a small room). */
    static final int TOP_FIVE = 5;

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final LeaderboardService leaderboardService;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;

    public TopFiveLeaderboardQueryService(SessionRepository sessionRepository,
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
    public TopFiveLeaderboardTransitionView transition(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        UUID questionId = session.getCurrentQuestionId();
        if (session.getState() != SessionState.IN_PROGRESS || questionId == null) {
            throw new NoCurrentQuestionException(session.getState());
        }
        if (session.getCurrentPhase() != SessionPhase.ANSWER_REVEALED
                && session.getCurrentPhase() != SessionPhase.LEADERBOARD) {
            throw new TopFiveLeaderboardNotAvailableException();
        }

        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        if (QuestionProgression.nextAfter(quiz, session).isEmpty()) {
            throw new TopFiveLeaderboardNotAvailableException();
        }

        List<Participant> participants = participantRepository.findBySessionId(sessionId);
        List<LeaderboardEntry> previous =
                leaderboardService.rankBefore(participants, session.roster(), questionId);
        List<LeaderboardEntry> current = leaderboardService.rank(participants, session.roster());

        Map<UUID, LeaderboardEntry> previousById = byParticipant(previous);
        Map<UUID, LeaderboardEntry> currentById = byParticipant(current);
        Map<UUID, Integer> pointsEarned = pointsEarnedOn(participants, questionId);

        // The two visible sets: whoever the board showed before, and
        // whoever it shows now. A participant in only one of them is an
        // entrant or a leaver, and the board they were absent from
        // contributes no rank — so the client neither invents movement out
        // of a rank it never displayed nor learns a position below fifth
        // (see TopFiveLeaderboardTransitionView.Entry).
        Set<UUID> previouslyVisible = topFiveIds(previous);
        Set<UUID> currentlyVisible = topFiveIds(current);

        List<TopFiveLeaderboardTransitionView.Entry> previousTopFive = previous.stream()
                .limit(TOP_FIVE)
                .map(entry -> entryFor(entry.participantId(), previousById, currentById,
                        pointsEarned, previouslyVisible, currentlyVisible))
                .toList();
        List<TopFiveLeaderboardTransitionView.Entry> currentTopFive = current.stream()
                .limit(TOP_FIVE)
                .map(entry -> entryFor(entry.participantId(), previousById, currentById,
                        pointsEarned, previouslyVisible, currentlyVisible))
                .toList();

        return new TopFiveLeaderboardTransitionView(
                sessionId,
                questionId,
                QuestionProgression.numberOf(quiz, session, questionId),
                quiz.questions().size(),
                false,
                previousTopFive,
                currentTopFive);
    }

    /**
     * One row of either board. A rank is carried only for the board this
     * participant was actually visible on: that is what keeps an entrant's
     * old rank (they were below fifth) and a leaver's new rank (they are
     * below fifth now) off the wire entirely, rather than trusting the
     * client not to render them.
     */
    private static TopFiveLeaderboardTransitionView.Entry entryFor(
            UUID participantId,
            Map<UUID, LeaderboardEntry> previousById,
            Map<UUID, LeaderboardEntry> currentById,
            Map<UUID, Integer> pointsEarned,
            Set<UUID> previouslyVisible,
            Set<UUID> currentlyVisible) {
        LeaderboardEntry previous = previousById.get(participantId);
        LeaderboardEntry current = currentById.get(participantId);
        return new TopFiveLeaderboardTransitionView.Entry(
                participantId,
                current != null ? current.displayName() : previous.displayName(),
                previouslyVisible.contains(participantId) && previous != null ? previous.rank() : null,
                currentlyVisible.contains(participantId) && current != null ? current.rank() : null,
                previous != null ? previous.score() : 0,
                current != null ? current.score() : 0,
                pointsEarned.getOrDefault(participantId, 0));
    }

    private static Set<UUID> topFiveIds(List<LeaderboardEntry> board) {
        return board.stream()
                .limit(TOP_FIVE)
                .map(LeaderboardEntry::participantId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<UUID, LeaderboardEntry> byParticipant(List<LeaderboardEntry> board) {
        Map<UUID, LeaderboardEntry> byId = new LinkedHashMap<>();
        board.forEach(entry -> byId.put(entry.participantId(), entry));
        return byId;
    }

    /**
     * What this question awarded each participant — read from their stored
     * answer, never derived by differencing the two boards.
     */
    private static Map<UUID, Integer> pointsEarnedOn(List<Participant> participants, UUID questionId) {
        Map<UUID, Integer> points = new LinkedHashMap<>();
        for (Participant participant : participants) {
            participant.answers().stream()
                    .filter(answer -> answer.questionId().equals(questionId))
                    .findFirst()
                    .map(ParticipantAnswer::pointsAwarded)
                    .ifPresent(awarded -> points.put(participant.getId(), awarded));
        }
        return points;
    }

}
