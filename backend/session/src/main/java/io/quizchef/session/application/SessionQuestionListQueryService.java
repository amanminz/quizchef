package io.quizchef.session.application;

import io.quizchef.identity.application.AuthorizationService;
import io.quizchef.identity.domain.CurrentUser;
import io.quizchef.identity.domain.Permission;
import io.quizchef.quiz.application.PlayableQuestionContentView;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.application.SessionQuestionListView.SessionQuestionView;
import io.quizchef.session.domain.Session;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The host's view of their session's questions — the read behind the live
 * screen's question panel, and the only way a host can act on a question
 * they are not currently showing.
 *
 * <p>Host only, and not merely by convention: it carries the answer key for
 * questions the room has not reached, which is exactly what a host needs to
 * fix a wrong one and exactly what a participant must never see. The
 * existing per-question read ({@link CurrentQuestionQueryService}) stays the
 * participant-facing one and stays phase-gated.
 *
 * <p>Removed questions are listed where they used to sit, without a number.
 * The host pulled them and should be able to see that they did; nothing
 * else in the engine can see them at all.
 */
@Service
public class SessionQuestionListQueryService {

    private final SessionRepository sessionRepository;
    private final SessionQuizQuery sessionQuizQuery;
    private final AuthorizationService authorizationService;

    public SessionQuestionListQueryService(SessionRepository sessionRepository,
                                           SessionQuizQuery sessionQuizQuery,
                                           AuthorizationService authorizationService) {
        this.sessionRepository = sessionRepository;
        this.sessionQuizQuery = sessionQuizQuery;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public SessionQuestionListView questions(CurrentUser currentUser, UUID sessionId) {
        authorizationService.authorize(currentUser, Permission.QUIZ_HOST);
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        SessionHostPolicy.requireHost(currentUser, session);

        PlayableQuizView effective = sessionQuizQuery.effectiveQuiz(session);
        PlayableQuizView everything = sessionQuizQuery.quizIncludingRemoved(session);
        Set<UUID> corrected = sessionQuizQuery.correctionsFor(session).keySet();

        // Displayed in the session's own order — removals kept in place, so
        // the host sees the sequence they built rather than a list that has
        // silently closed over the gap they made.
        List<PlayableQuestion> ordered = QuestionProgression.orderFor(everything, session);
        UUID currentQuestionId = session.getCurrentQuestionId();
        int currentNumber = currentQuestionId == null
                ? 0
                : QuestionProgression.numberOf(effective, session, currentQuestionId);

        List<SessionQuestionView> rows = new ArrayList<>(ordered.size());
        for (PlayableQuestion question : ordered) {
            UUID questionId = question.questionId();
            boolean removed = session.isRemoved(questionId);
            // Numbering comes from the effective sequence, never from this
            // list — that is what makes it close up with no gap, and what
            // keeps the host's "Question 3 of 4" identical to the players'.
            int number = removed ? 0 : QuestionProgression.numberOf(effective, session, questionId);
            PlayableQuestionContentView content = sessionQuizQuery.effectiveContent(session, questionId);
            rows.add(new SessionQuestionView(
                    questionId,
                    removed ? null : number,
                    statusOf(removed, number, currentNumber, questionId, currentQuestionId),
                    corrected.contains(questionId),
                    content.questionType(),
                    content.defaultLanguage(),
                    question.correctOptionIds(),
                    content.options(),
                    content.localizations()));
        }
        return new SessionQuestionListView(sessionId, effective.questions().size(), List.copyOf(rows));
    }

    private static SessionQuestionStatus statusOf(boolean removed, int number, int currentNumber,
                                                  UUID questionId, UUID currentQuestionId) {
        if (removed) {
            return SessionQuestionStatus.REMOVED;
        }
        if (questionId.equals(currentQuestionId)) {
            return SessionQuestionStatus.CURRENT;
        }
        return currentNumber > 0 && number < currentNumber
                ? SessionQuestionStatus.PLAYED
                : SessionQuestionStatus.UPCOMING;
    }
}
