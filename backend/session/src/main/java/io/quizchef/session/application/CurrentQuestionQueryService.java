package io.quizchef.session.application;

import io.quizchef.quiz.application.GameplayQuestionContentQuery;
import io.quizchef.quiz.application.GameplayQuizQuery;
import io.quizchef.quiz.application.PlayableQuestionContentView;
import io.quizchef.quiz.application.PlayableQuizView;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.SessionPhase;
import io.quizchef.session.domain.SessionState;
import io.quizchef.session.domain.exception.NoCurrentQuestionException;
import io.quizchef.session.infrastructure.persistence.SessionRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of live gameplay: the question currently in play, with its
 * participant-safe content. Public like the session summary — a session is
 * reached by its unguessable id, and the players it serves are anonymous
 * guests by nature. What the response admits is phase-gated here, in one
 * place: content only while a question is actually in play (never during
 * the lobby, never by probing a PIN'd session before start), correct
 * option ids only once revealed. Time remaining comes from the shared
 * {@link Clock} (ADR-006) — the client renders it, never decides it.
 */
@Service
public class CurrentQuestionQueryService {

    private final SessionRepository sessionRepository;
    private final GameplayQuizQuery gameplayQuizQuery;
    private final GameplayQuestionContentQuery contentQuery;
    private final Clock clock;

    public CurrentQuestionQueryService(SessionRepository sessionRepository,
                                       GameplayQuizQuery gameplayQuizQuery,
                                       GameplayQuestionContentQuery contentQuery,
                                       Clock clock) {
        this.sessionRepository = sessionRepository;
        this.gameplayQuizQuery = gameplayQuizQuery;
        this.contentQuery = contentQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CurrentQuestionView currentQuestion(UUID sessionId) {
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        UUID questionId = session.getCurrentQuestionId();
        if (session.getState() != SessionState.IN_PROGRESS || questionId == null) {
            throw new NoCurrentQuestionException(session.getState());
        }

        PlayableQuizView quiz = gameplayQuizQuery.load(session.getPublishedQuizVersionId());
        PlayableQuestionContentView content = contentQuery.content(questionId);

        boolean revealed = session.getCurrentPhase() == SessionPhase.ANSWER_REVEALED
                || session.getCurrentPhase() == SessionPhase.LEADERBOARD;
        boolean open = session.getCurrentPhase() == SessionPhase.QUESTION_OPEN
                && session.getCurrentQuestionTimer() != null;

        return new CurrentQuestionView(
                session.getId(),
                session.getCurrentPhase(),
                questionNumber(quiz, questionId),
                quiz.questions().size(),
                quiz.questionTimeLimitSeconds(),
                open ? session.getCurrentQuestionTimer().endsAt() : null,
                open ? remainingMillis(session) : 0L,
                content,
                revealed ? correctOptionIds(quiz, questionId) : null);
    }

    private long remainingMillis(Session session) {
        long remaining = Duration.between(clock.instant(),
                session.getCurrentQuestionTimer().endsAt()).toMillis();
        return Math.max(0L, remaining);
    }

    private static int questionNumber(PlayableQuizView quiz, UUID questionId) {
        List<PlayableQuizView.PlayableQuestion> questions = quiz.questions();
        for (int index = 0; index < questions.size(); index++) {
            if (questions.get(index).questionId().equals(questionId)) {
                return index + 1;
            }
        }
        return 0;
    }

    private static Set<UUID> correctOptionIds(PlayableQuizView quiz, UUID questionId) {
        return quiz.questions().stream()
                .filter(question -> question.questionId().equals(questionId))
                .findFirst()
                .map(PlayableQuizView.PlayableQuestion::correctOptionIds)
                .orElse(Set.of());
    }
}
