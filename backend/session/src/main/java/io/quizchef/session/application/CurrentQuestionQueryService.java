package io.quizchef.session.application;

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
 * the lobby, never by probing a PIN'd session before start), options
 * (structure and text) only once the reading period ends — {@code
 * QUESTION_PREVIEW} shows the prompt alone — and correct option ids and the
 * author's explanation only once revealed; both are reveal-time material,
 * an explanation routinely gives the answer away. {@code endsAt}/{@code
 * remainingMillis} describe whichever countdown the current phase is
 * running (the reading-period clock during {@code QUESTION_PREVIEW}, the
 * answer clock during {@code QUESTION_OPEN}), both from the shared
 * {@link Clock} (ADR-006) — the client renders it, never decides it.
 */
@Service
public class CurrentQuestionQueryService {

    private final SessionRepository sessionRepository;
    private final SessionQuizQuery sessionQuizQuery;
    private final Clock clock;

    public CurrentQuestionQueryService(SessionRepository sessionRepository,
                                       SessionQuizQuery sessionQuizQuery,
                                       Clock clock) {
        this.sessionRepository = sessionRepository;
        this.sessionQuizQuery = sessionQuizQuery;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public CurrentQuestionView currentQuestion(UUID sessionId) {
        Session session = SessionLookup.byId(sessionRepository, sessionId);
        UUID questionId = session.getCurrentQuestionId();
        if (session.getState() != SessionState.IN_PROGRESS || questionId == null) {
            throw new NoCurrentQuestionException(session.getState());
        }

        PlayableQuizView quiz = sessionQuizQuery.effectiveQuiz(session);
        PlayableQuestionContentView content = sessionQuizQuery.effectiveContent(session, questionId);

        boolean previewing = session.getCurrentPhase() == SessionPhase.QUESTION_PREVIEW;
        boolean revealed = session.getCurrentPhase() == SessionPhase.ANSWER_REVEALED
                || session.getCurrentPhase() == SessionPhase.LEADERBOARD;
        boolean open = session.getCurrentPhase() == SessionPhase.QUESTION_OPEN
                && session.getCurrentQuestionTimer() != null;
        // The reused countdown (Session.currentQuestionTimer, ADR-004's single
        // "current phase's clock" field) is active during either open answering
        // or the reading period — never both, and null otherwise.
        boolean countingDown = (open || previewing) && session.getCurrentQuestionTimer() != null;

        PlayableQuestionContentView displayedContent = revealed ? content : withoutExplanations(content);
        if (previewing) {
            displayedContent = withoutOptions(displayedContent);
        }

        return new CurrentQuestionView(
                session.getId(),
                session.getCurrentPhase(),
                QuestionProgression.numberOf(quiz, session, questionId),
                quiz.questions().size(),
                quiz.questionTimeLimitSeconds(),
                countingDown ? session.getCurrentQuestionTimer().endsAt() : null,
                countingDown ? remainingMillis(session) : 0L,
                displayedContent,
                revealed ? correctOptionIds(quiz, questionId) : null);
    }

    /** Explanations are reveal-time material — withheld while answers are still possible. */
    private static PlayableQuestionContentView withoutExplanations(PlayableQuestionContentView content) {
        return new PlayableQuestionContentView(
                content.questionId(),
                content.questionType(),
                content.defaultLanguage(),
                content.options(),
                content.localizations().stream()
                        .map(PlayableQuestionContentView.PlayableLocalizationView::withoutExplanation)
                        .toList());
    }

    /**
     * Options are reading-period material — the question is current and its
     * prompt is visible, but no option (structure or text, in any language)
     * crosses the wire until the reading period ends. Not merely hidden by
     * the client: the field is genuinely absent from the response.
     */
    private static PlayableQuestionContentView withoutOptions(PlayableQuestionContentView content) {
        return new PlayableQuestionContentView(
                content.questionId(),
                content.questionType(),
                content.defaultLanguage(),
                List.of(),
                content.localizations().stream()
                        .map(localization -> new PlayableQuestionContentView.PlayableLocalizationView(
                                localization.languageCode(), localization.prompt(),
                                localization.explanation(), List.of()))
                        .toList());
    }

    private long remainingMillis(Session session) {
        long remaining = Duration.between(clock.instant(),
                session.getCurrentQuestionTimer().endsAt()).toMillis();
        return Math.max(0L, remaining);
    }


    private static Set<UUID> correctOptionIds(PlayableQuizView quiz, UUID questionId) {
        return quiz.questions().stream()
                .filter(question -> question.questionId().equals(questionId))
                .findFirst()
                .map(PlayableQuizView.PlayableQuestion::correctOptionIds)
                .orElse(Set.of());
    }
}
