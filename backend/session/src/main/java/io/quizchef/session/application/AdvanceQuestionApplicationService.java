package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.exception.InvalidSessionTransitionException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.Optional;
import java.util.UUID;
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

    private final SessionRepository sessionRepository;
    private final SessionQuizQuery sessionQuizQuery;
    private final AuthorizationService authorizationService;
    private final QuestionOpener questionOpener;
    private final SessionFinisher sessionFinisher;

    public AdvanceQuestionApplicationService(SessionRepository sessionRepository,
                                             SessionQuizQuery sessionQuizQuery,
                                             AuthorizationService authorizationService,
                                             QuestionOpener questionOpener,
                                             SessionFinisher sessionFinisher) {
        this.sessionRepository = sessionRepository;
        this.sessionQuizQuery = sessionQuizQuery;
        this.authorizationService = authorizationService;
        this.questionOpener = questionOpener;
        this.sessionFinisher = sessionFinisher;
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

        PlayableQuizView quiz = sessionQuizQuery.effectiveQuiz(session);
        Optional<PlayableQuestion> next =
                QuestionProgression.nextAfter(quiz, session);
        if (phase == SessionPhase.ANSWER_REVEALED && next.isPresent()) {
            throw new InvalidSessionTransitionException(session.getState(),
                    "advance before the leaderboard is shown");
        }
        if (next.isPresent()) {
            questionOpener.startPreview(session, next.get(), quiz.questionTimeLimitSeconds());
        } else {
            sessionFinisher.finish(session, "after the last question");
        }
        sessionRepository.saveAndFlush(session);
        return SessionSummaryView.of(session);
    }
}
