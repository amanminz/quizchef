package io.quizchef.session.application;

import io.quizchef.common.event.DomainEventPublisher;
import io.quizchef.quiz.application.PlayableQuizView.PlayableQuestion;
import io.quizchef.session.domain.QuestionTimer;
import io.quizchef.session.domain.Session;
import io.quizchef.session.domain.event.QuestionStartedEvent;
import java.time.Clock;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Opens a question on a session — the shared heart of "start" and "advance".
 * Builds the timer from the server clock (ADR-006), transitions the aggregate
 * to QUESTION_OPEN, announces it, and arms the server-side close. The caller
 * persists the session.
 */
@Component
class QuestionOpener {

    private final DomainEventPublisher eventPublisher;
    private final QuestionTimerScheduler timerScheduler;
    private final Clock clock;

    QuestionOpener(DomainEventPublisher eventPublisher, QuestionTimerScheduler timerScheduler, Clock clock) {
        this.eventPublisher = eventPublisher;
        this.timerScheduler = timerScheduler;
        this.clock = clock;
    }

    void open(Session session, PlayableQuestion question, int timeLimitSeconds) {
        QuestionTimer timer = QuestionTimer.startingAt(
                clock.instant(), Duration.ofSeconds(timeLimitSeconds));
        session.openQuestion(question.questionId(), timer);
        eventPublisher.publish(new QuestionStartedEvent(session.getId(), question.questionId(),
                timer.endsAt(), timer.durationSeconds(), clock.instant()));
        timerScheduler.scheduleClose(session.getId(), question.questionId(), timer.endsAt());
    }
}
