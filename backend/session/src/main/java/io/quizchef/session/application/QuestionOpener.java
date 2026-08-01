package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.QuestionPreviewStartedEvent;
import io.quizchef.session.infrastructure.GameplayProperties;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Starts a question's reading period on a session — the shared heart of
 * "start" and "advance". Builds the preview timer from the server clock
 * (ADR-006) and {@link GameplayProperties#questionPreviewSeconds()},
 * transitions the aggregate to QUESTION_PREVIEW, announces it, and arms the
 * scheduler to open the question for answers once the reading period ends
 * ({@link OpenQuestionApplicationService}, not this class — opening is a
 * fully automatic, later transition, not part of "starting"). The caller
 * persists the session.
 */
@Component
class QuestionOpener {

    private final DomainEventPublisher eventPublisher;
    private final QuestionTimerScheduler timerScheduler;
    private final GameplayProperties gameplayProperties;
    private final Clock clock;

    QuestionOpener(DomainEventPublisher eventPublisher, QuestionTimerScheduler timerScheduler,
                   GameplayProperties gameplayProperties, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.timerScheduler = timerScheduler;
        this.gameplayProperties = gameplayProperties;
        this.clock = clock;
    }

    void startPreview(Session session, PlayableQuestion question, int answerTimeLimitSeconds) {
        QuestionTimer previewTimer = QuestionTimer.startingAt(
                clock.instant(), Duration.ofSeconds(gameplayProperties.questionPreviewSeconds()));
        session.previewQuestion(question.questionId(), previewTimer);
        eventPublisher.publish(new QuestionPreviewStartedEvent(session.getId(), question.questionId(),
                previewTimer.endsAt(), previewTimer.durationSeconds(), clock.instant()));
        // The answer duration travels with the scheduled callback (like
        // sessionId/questionId already do) rather than being looked up
        // again when the preview ends — it is simply the quiz's per-question
        // time limit the caller already resolved.
        timerScheduler.schedulePreviewEnd(session.getId(), question.questionId(),
                previewTimer.endsAt(), answerTimeLimitSeconds);
    }
}
