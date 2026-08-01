package io.quizchef.session.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for arming the server-side question timers. When a question opens,
 * the engine asks the scheduler to close it at {@code endsAt} if the host
 * has not already done so; when a question enters its reading period, the
 * engine asks the scheduler to open it for answers once the preview ends —
 * the server, not the client, decides when each clock runs out (ADR-006).
 * The scheduling mechanism is an infrastructure detail behind this
 * interface.
 */
public interface QuestionTimerScheduler {

    void scheduleClose(UUID sessionId, UUID questionId, Instant endsAt);

    void schedulePreviewEnd(UUID sessionId, UUID questionId, Instant previewEndsAt, int answerDurationSeconds);
}
