package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.FinalStanding;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.infrastructure.persistence.FinalStandingRepository;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Advances from the leaderboard to the next question, or finishes the session
 * when the quiz is exhausted. Host only. The host decides <em>when</em> to
 * move on; the engine decides <em>where</em> to (ADR-006).
 *
 * <p>Two phases may advance, and which one is legal follows from what the
 * engine is about to do:
 *
 * <ul>
 *   <li>{@code LEADERBOARD} → open the next question. A non-final question
 *       still passes through its interim standings first; skipping them
 *       would skip the between-questions leaderboard the host projects.</li>
 *   <li>{@code ANSWER_REVEALED} → finish, and <em>only</em> when the quiz
 *       is exhausted. The last question has no leaderboard step at all
 *       ({@link ShowLeaderboardApplicationService} refuses it), so
 *       requiring one before finishing would strand the host in a phase
 *       they can no longer reach.</li>
 * </ul>
 *
 * <p>A non-final question therefore cannot be advanced straight from its
 * reveal — that would be a host skipping the standings, not the engine
 * saving a step.
 */
@Service
public class AdvanceQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AdvanceQuestionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final FinalStandingRepository finalStandingRepository;
    private final LeaderboardService leaderboardService;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;
    private final QuestionOpener questionOpener;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public AdvanceQuestionApplicationService(SessionRepository sessionRepository,
                                             ParticipantRepository participantRepository,
                                             FinalStandingRepository finalStandingRepository,
                                             LeaderboardService leaderboardService,
                                             GameplayQuizQuery gameplayQuizQuery,
                                             AuthorizationService authorizationService,
                                             QuestionOpener questionOpener,
                                             DomainEventPublisher eventPublisher,
                                             Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.finalStandingRepository = finalStandingRepository;
        this.leaderboardService = leaderboardService;
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.authorizationService = authorizationService;
        this.questionOpener = questionOpener;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView advance(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);
        SessionPhase phase = session.getCurrentPhase();
        if (phase != SessionPhase.LEADERBOARD && phase != SessionPhase.ANSWER_REVEALED) {
            throw new InvalidSessionTransitionException(session.getState(),
                    "advance before the leaderboard is shown");
        }

        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        Optional<PlayableQuestion> next =
                QuestionProgression.nextAfter(quiz, session);
        if (phase == SessionPhase.ANSWER_REVEALED && next.isPresent()) {
            throw new InvalidSessionTransitionException(session.getState(),
                    "advance before the leaderboard is shown");
        }
        if (next.isPresent()) {
            questionOpener.startPreview(session, next.get(), quiz.questionTimeLimitSeconds());
        } else {
            // Capture the standings *before* finishing, while the ranking is
            // still the one this game actually produced. This is the only
            // place a session ends, so it is the only place history is
            // written — and it is written once, never updated.
            captureFinalStandings(session);
            session.finish();
            eventPublisher.publish(new SessionFinishedEvent(sessionId, clock.instant()));
            log.info("Session {} finished after the last question", sessionId);
        }
        sessionRepository.saveAndFlush(session);
        return SessionSummaryView.of(session);
    }

    /**
     * Copies the finishing order into durable history: name, rank, and score
     * as they stood at this moment.
     *
     * <p>The rank is stored rather than recomputed later on purpose. The
     * host's live standings are projected and ranked at read time (ADR-006),
     * which is right for a running game and wrong for a finished one — a
     * change to the ranking rule would rewrite the result of an event that
     * already happened, and a display name edited afterwards would
     * retroactively rename someone in a past quiz.
     *
     * <p>Idempotent by guard as well as by construction: advancing a
     * finished session already throws, but a snapshot that exists is never
     * written twice.
     */
    private void captureFinalStandings(Session session) {
        if (finalStandingRepository.existsBySessionId(session.getId())) {
            return;
        }
        Instant capturedAt = clock.instant();
        List<FinalStanding> standings = leaderboardService
                .rank(participantRepository.findBySessionId(session.getId()), session.roster())
                .stream()
                .map(entry -> FinalStanding.capture(session.getId(), entry, capturedAt))
                .toList();
        finalStandingRepository.saveAll(standings);
        log.info("Captured {} final standings for session {}", standings.size(), session.getId());
    }
}
