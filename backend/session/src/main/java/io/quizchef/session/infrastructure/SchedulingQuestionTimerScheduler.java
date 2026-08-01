package io.quizchef.session.infrastructure;

import io.quizchef.session.application.CloseQuestionApplicationService;
import io.quizchef.session.application.OpenQuestionApplicationService;
import io.quizchef.session.application.QuestionTimerScheduler;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Arms the question timers with a Spring {@link TaskScheduler}: at {@code
 * endsAt} it asks the engine to close the question; at {@code
 * previewEndsAt} it asks the engine to open the question for answers.
 * Both are idempotent against the aggregate's current state — if the host
 * closed early, or play has already moved on, nothing happens — so a fired
 * timer can never disturb a later question.
 */
@Component
public class SchedulingQuestionTimerScheduler implements QuestionTimerScheduler {

    private static final Logger log = LoggerFactory.getLogger(SchedulingQuestionTimerScheduler.class);

    private final TaskScheduler taskScheduler;
    private final CloseQuestionApplicationService closeQuestionApplicationService;
    private final OpenQuestionApplicationService openQuestionApplicationService;

    public SchedulingQuestionTimerScheduler(@Qualifier("gameplayTaskScheduler") TaskScheduler taskScheduler,
                                            CloseQuestionApplicationService closeQuestionApplicationService,
                                            OpenQuestionApplicationService openQuestionApplicationService) {
        this.taskScheduler = taskScheduler;
        this.closeQuestionApplicationService = closeQuestionApplicationService;
        this.openQuestionApplicationService = openQuestionApplicationService;
    }

    @Override
    public void scheduleClose(UUID sessionId, UUID questionId, Instant endsAt) {
        taskScheduler.schedule(() -> closeOnExpiry(sessionId, questionId), endsAt);
    }

    @Override
    public void schedulePreviewEnd(UUID sessionId, UUID questionId, Instant previewEndsAt,
                                   int answerDurationSeconds) {
        taskScheduler.schedule(
                () -> openOnPreviewEnd(sessionId, questionId, answerDurationSeconds), previewEndsAt);
    }

    private void closeOnExpiry(UUID sessionId, UUID questionId) {
        try {
            closeQuestionApplicationService.closeIfExpired(sessionId, questionId);
        } catch (RuntimeException exception) {
            log.warn("Timer close failed for session {} question {}: {}",
                    sessionId, questionId, exception.getMessage());
        }
    }

    private void openOnPreviewEnd(UUID sessionId, UUID questionId, int answerDurationSeconds) {
        try {
            openQuestionApplicationService.openIfPreviewExpired(sessionId, questionId, answerDurationSeconds)
                    .ifPresent(answerEndsAt -> scheduleClose(sessionId, questionId, answerEndsAt));
        } catch (RuntimeException exception) {
            log.warn("Timer preview-open failed for session {} question {}: {}",
                    sessionId, questionId, exception.getMessage());
        }
    }
}
