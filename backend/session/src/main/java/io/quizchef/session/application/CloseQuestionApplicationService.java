package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.QuestionClosedEvent;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the current question to answers — either the host closing early or
 * the server-armed timer expiring. Both paths converge here; whichever wins,
 * the other is a no-op (idempotent), so a fired timer can never disturb a
 * later question.
 */
@Service
public class CloseQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(CloseQuestionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final AuthorizationService authorizationService;
    private final DomainEventPublisher eventPublisher;
    private final Clock clock;

    public CloseQuestionApplicationService(SessionRepository sessionRepository,
                                           AuthorizationService authorizationService,
                                           DomainEventPublisher eventPublisher,
                                           Clock clock) {
        this.sessionRepository = sessionRepository;
        this.authorizationService = authorizationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Host closes the current question early.
     */
    @Transactional
    public SessionSummaryView close(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        UUID questionId = session.getCurrentQuestionId();
        session.closeQuestion();
        sessionRepository.saveAndFlush(session);

        eventPublisher.publish(new QuestionClosedEvent(sessionId, questionId, clock.instant()));
        return SessionSummaryView.of(session);
    }

    /**
     * Timer expiry closes the question if it is still open — a no-op if the
     * host already closed it or play has moved on. No caller; the scheduler
     * invokes it, so there is no authorization here (a system trigger, not a
     * user command).
     */
    @Transactional
    public void closeIfExpired(UUID sessionId, UUID questionId) {
        Session session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null || !session.acceptsAnswersFor(questionId)) {
            return;
        }
        session.closeQuestion();
        sessionRepository.saveAndFlush(session);
        eventPublisher.publish(new QuestionClosedEvent(sessionId, questionId, clock.instant()));
        log.info("Question {} in session {} closed by timer", questionId, sessionId);
    }
}
