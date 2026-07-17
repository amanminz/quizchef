package io.quizchef.session.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Port for arming the server-side question timer. When a question opens, the
 * engine asks the scheduler to close it at {@code endsAt} if the host has
 * not already done so — the server, not the client, decides when time is up
 * (ADR-006). The scheduling mechanism is an infrastructure detail behind
 * this interface.
 */
public interface QuestionTimerScheduler {

    void scheduleClose(UUID sessionId, UUID questionId, Instant endsAt);
}
