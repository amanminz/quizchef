package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.event.SessionFinishedEvent;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
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
 */
@Service
public class AdvanceQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(AdvanceQuestionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;
    private final QuestionOpener questionOpener;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public AdvanceQuestionApplicationService(SessionRepository sessionRepository,
                                             GameplayQuizQuery gameplayQuizQuery,
                                             AuthorizationService authorizationService,
                                             QuestionOpener questionOpener,
                                             DomainEventPublisher eventPublisher,
                                             Clock clock) {
        this.sessionRepository = sessionRepository;
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
        if (session.getCurrentPhase() != SessionPhase.LEADERBOARD) {
            throw new InvalidSessionTransitionException(session.getState(),
                    "advance before the leaderboard is shown");
        }

        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        Optional<PlayableQuestion> next =
                QuestionProgression.nextAfter(quiz, session.getCurrentQuestionId());
        if (next.isPresent()) {
            questionOpener.open(session, next.get(), quiz.questionTimeLimitSeconds());
        } else {
            session.finish();
            eventPublisher.publish(new SessionFinishedEvent(sessionId, clock.instant()));
            log.info("Session {} finished after the last question", sessionId);
        }
        sessionRepository.saveAndFlush(session);
        return SessionSummaryView.of(session);
    }
}
