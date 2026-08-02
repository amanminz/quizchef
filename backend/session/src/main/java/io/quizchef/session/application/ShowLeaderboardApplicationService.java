package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.session.domain.LeaderboardEntry;
import io.quizchef.session.domain.LeaderboardService;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.LeaderboardUpdatedEvent;
import io.quizchef.session.domain.exception.LeaderboardNotAvailableException;
import io.quizchef.session.infrastructure.persistence.ParticipantRepository;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Projects the standings between questions. Host only. The leaderboard is
 * computed fresh from participants' cached scores — never persisted
 * (ADR-006) — and broadcast.
 *
 * <p><strong>Between</strong> questions, and only between them: the quiz's
 * last question has no leaderboard step at all. Its standings are the
 * final ones, and those belong to the host's podium ceremony — showing
 * them as an ordinary interim board first would spoil the reveal on the
 * projector and (until the release gate) put the finishing order on the
 * screen ahead of it. The refusal lives here rather than only in the host
 * client so no client, stale tab, or retry can produce that screen; the
 * host's step after the last reveal is to finish the session, which
 * {@link AdvanceQuestionApplicationService} accepts directly from
 * {@code ANSWER_REVEALED} for exactly this reason.
 */
@Service
public class ShowLeaderboardApplicationService {

    private final SessionRepository sessionRepository;
    private final ParticipantRepository participantRepository;
    private final LeaderboardService leaderboardService;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public ShowLeaderboardApplicationService(SessionRepository sessionRepository,
                                             ParticipantRepository participantRepository,
                                             LeaderboardService leaderboardService,
                                             GameplayQuizQuery gameplayQuizQuery,
                                             AuthorizationService authorizationService,
                                             DomainEventPublisher eventPublisher,
                                             Clock clock) {
        this.sessionRepository = sessionRepository;
        this.participantRepository = participantRepository;
        this.leaderboardService = leaderboardService;
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public List<LeaderboardEntry> show(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        if (QuestionProgression.nextAfter(quiz, session.getCurrentQuestionId()).isEmpty()) {
            throw new LeaderboardNotAvailableException();
        }

        session.showLeaderboard();
        sessionRepository.saveAndFlush(session);

        List<LeaderboardEntry> leaderboard = leaderboardService.rank(
                participantRepository.findBySessionId(sessionId), session.roster());
        eventPublisher.publish(new LeaderboardUpdatedEvent(sessionId, leaderboard, clock.instant()));
        return leaderboard;
    }
}
