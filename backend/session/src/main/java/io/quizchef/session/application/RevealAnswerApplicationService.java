package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.AnswerRevealedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reveals the correct answer for the closed question. Host only. This is the
 * first moment correctness crosses the wire (ADR-006) — the correct option
 * ids ride the event to every client.
 */
@Service
public class RevealAnswerApplicationService {

    private final SessionRepository sessionRepository;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public RevealAnswerApplicationService(SessionRepository sessionRepository,
                                          GameplayQuizQuery gameplayQuizQuery,
                                          AuthorizationService authorizationService,
                                          DomainEventPublisher eventPublisher,
                                          Clock clock) {
        this.sessionRepository = sessionRepository;
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public SessionSummaryView reveal(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        UUID questionId = session.getCurrentQuestionId();
        session.revealAnswer();
        sessionRepository.saveAndFlush(session);

        Set<UUID> correctOptionIds = gameplayQuizQuery.load(session.getPublishedQuizVersionId())
                .questions().stream()
                .filter(question -> question.questionId().equals(questionId))
                .findFirst()
                .map(PlayableQuestion::correctOptionIds)
                .orElse(Set.of());
        eventPublisher.publish(new AnswerRevealedEvent(sessionId, questionId, correctOptionIds, clock.instant()));
        return SessionSummaryView.of(session);
    }
}
