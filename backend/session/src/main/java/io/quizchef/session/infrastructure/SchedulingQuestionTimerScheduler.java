package io.quizchef.session.infrastructure;

import io.quizchef.session.application.CloseQuestionApplicationService;
import io.quizchef.session.application.QuestionTimerScheduler;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Arms the question timer with a Spring {@link TaskScheduler}: at {@code
 * endsAt} it asks the engine to close the question. The close is idempotent
 * — if the host closed it first, or play has already moved on, nothing
 * happens — so a fired timer can never disturb a later question.
 */
@Component
public class SchedulingQuestionTimerScheduler implements QuestionTimerScheduler {

    private static final Logger log = LoggerFactory.getLogger(SchedulingQuestionTimerScheduler.class);

    private final TaskScheduler taskScheduler;
    private final CloseQuestionApplicationService closeQuestionApplicationService;

    public SchedulingQuestionTimerScheduler(@Qualifier("gameplayTaskScheduler") TaskScheduler taskScheduler,
                                            CloseQuestionApplicationService closeQuestionApplicationService) {
        this.taskScheduler = taskScheduler;
        this.closeQuestionApplicationService = closeQuestionApplicationService;
    }

    @Override
    public void scheduleClose(UUID sessionId, UUID questionId, Instant endsAt) {
        taskScheduler.schedule(() -> closeOnExpiry(sessionId, questionId), endsAt);
    }

    private void closeOnExpiry(UUID sessionId, UUID questionId) {
        try {
            closeQuestionApplicationService.closeIfExpired(sessionId, questionId);
        } catch (RuntimeException exception) {
            log.warn("Timer close failed for session {} question {}: {}",
                    sessionId, questionId, exception.getMessage());
        }
    }
}
