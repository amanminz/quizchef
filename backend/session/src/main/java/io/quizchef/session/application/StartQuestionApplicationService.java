package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.exception.SessionNotStartableException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opens the first question of a started session. Host only. The engine — not
 * the host — chooses the question: the first in the quiz's authored order.
 */
@Service
public class StartQuestionApplicationService {

    private static final Logger log = LoggerFactory.getLogger(StartQuestionApplicationService.class);

    private final SessionRepository sessionRepository;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final AuthorizationService authorizationService;
    private final QuestionOpener questionOpener;

    public StartQuestionApplicationService(SessionRepository sessionRepository,
                                           GameplayQuizQuery gameplayQuizQuery,
                                           AuthorizationService authorizationService,
                                           QuestionOpener questionOpener) {
        this.sessionRepository = sessionRepository;
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.authorizationService = authorizationService;
        this.questionOpener = questionOpener;
    }

    @Transactional
    public SessionSummaryView start(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        var first = QuestionProgression.nextAfter(quiz, session.getCurrentQuestionId())
                .orElseThrow(() -> new SessionNotStartableException("This quiz has no questions to start"));
        questionOpener.open(session, first, quiz.questionTimeLimitSeconds());
        sessionRepository.saveAndFlush(session);

        log.info("Session {} opened first question {}", session.getId(), first.questionId());
        return SessionSummaryView.of(session);
    }
}
